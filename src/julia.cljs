(ns lt.objs.langs.julia
  (:require [lt.objs.langs.julia.util :as util]
            [lt.objs.langs.julia.light-lines :as lights]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.objs.console :as console]
            [lt.objs.command :as cmd]
            [lt.objs.clients.tcp :as tcp]
            [lt.objs.sidebar.clients :as scl]
            [lt.objs.dialogs :as dialogs]
            [lt.objs.files :as files]
            [lt.objs.popup :as popup]
            [lt.objs.platform :as platform]
            [lt.plugins.auto-complete :as auto-complete]
            [lt.objs.editor :as ed]
            [lt.objs.plugins :as plugins]
            [lt.plugins.watches :as watches]
            [clojure.string :as string]
            [lt.objs.clients :as clients]
            [lt.objs.notifos :as notifos]
            [lt.objs.cache :as cache]
            [lt.util.load :as load]
            [lt.util.cljs]; :refer [js->clj]]
            [lt.objs.editor :as editor]
            [lt.objs.editor.pool :as pool]
            [lt.plugins.doc :as doc]
            [crate.core :as crate])
  (:require-macros [lt.macros :refer [behavior defui]]))

;; Editor commands

; Should really factor these into behaviours
(behavior ::editor-commands
  :triggers #{:editor.eval.julia.editor-command}
  :reaction (fn [editor res]
              (notifos/done-working)
              (condp = (res :cmd)
                "result" (do
                           (notifos/done-working "")
                           (let [val (if (res :html)
                                       (-> res :value crate/raw)
                                       (-> res :value))
                                 scripts (when (res :html) (util/get-scripts val))]
                             (object/raise editor
                                           (if (res :under)
                                             :editor.result.underline
                                             :editor.result)
                                           val
                                           {:start-line (-> res :start dec)
                                            :line (-> res :end dec)})
                             (when scripts (util/eval-scripts scripts))))
                "error" (do
                          (notifos/done-working "")
                          (let [dom (-> res :value util/parse-div)
                                line (-> res :end dec)]
                            (util/process-links! dom editor)
                            (object/raise editor
                                          :editor.exception
                                          dom
                                          {:start-line (-> res :start dec)
                                           :line line})
                            (lights/clear)
                            (lights/add (util/get-error-lines dom))
                            (let [ex (-> @editor :widgets (get [(editor/line-handle editor line) :inline]))]
                              (lights/listen ex)))))))

;; Global commands

(behavior ::commands
  :triggers #{:editor.eval.julia.command}
  :reaction (fn [editor res]
              (condp = (res :cmd)
                "popup" (popup/popup! {:header  (res :header)
                                       :body    (res :body)
                                       :buttons (res :buttons)})
                "print" (console/log (res :value)
                                     (if (res :error) "error"))
                "done"  (if (res :msg)
                          (notifos/done-working (res :msg))
                          (notifos/done-working))
                "notify" (notifos/set-msg! (res :msg) {:class (res :class)})
                "console" (if (res :html)
                            (let [val (-> res :value crate/raw)
                                  scripts (util/get-scripts val)]
                              (console/verbatim val)
                              (util/eval-scripts scripts))
                            (-> res :value console/log)))))

(object/object* ::julia-lang
                :tags #{:julia.lang}
                :behaviors [::commands])

(def julia (object/create ::julia-lang))

(cmd/command {:command :editor.interrupt-clients
              :desc "Julia: Interrupt the current client"
              :exec (fn []
                      (when-let [ed (pool/last-active)]
                        (doseq [proc (->> @ed :client vals (map deref) (map :proc) (filter identity))]
                          (.kill proc "SIGINT"))
                        (notifos/done-working)))})

(cmd/command {:command :editor.kill-clients
              :desc "Julia: Kill the current client"
              :exec (fn []
                      (when-let [ed (pool/last-active)]
                        (doseq [proc (->> @ed :client vals (map deref) (map :proc) (filter identity))]
                            (.kill proc))
                        (notifos/done-working)))})

;; Settings

(behavior ::julia-path
          :triggers #{:object.instant}
          :desc "Julia: Set the path to the Julia executable"
          :type :user
          :params [{:label "path"
                    :type :path}]
          :exclusive true
          :reaction (fn [this exe]
                      (object/merge! julia {:path exe})))
