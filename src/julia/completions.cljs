(ns lt.objs.langs.julia.completions
  (:require [lt.objs.langs.julia.proc :as proc]
            [lt.objs.langs.julia.util :as util]
            [lt.objs.clients :as clients]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.plugins.auto-complete :as auto-complete]
            [lt.objs.editor :as editor])
  (:require-macros [lt.macros :refer [behavior defui]]))

(behavior ::trigger-update-hints
          :triggers #{:editor.julia.hints.trigger-update}
          :reaction (fn [editor res]
                      (when-let [default-client (-> @editor :client :default)] ;; dont eval unless we're already connected
                        (when @default-client
                          (clients/send (eval/get-client! {:command :editor.julia.hints
                                                           :info {}
                                                           :origin editor
                                                           :create proc/connect})
                                        :editor.julia.hints
                                        {:cursor (util/cursor editor)
                                         :code (editor/->val editor)}
                                        :only editor)))))

(behavior ::use-local-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (if (::fresh-hints @editor)
                        (object/merge! editor {::fresh-hints false})
                        (object/raise editor :editor.julia.hints.trigger-update))
                      (concat (::hints @editor) hints)))

(behavior ::textual-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints]
                      (if-not (::no-textual-hints @editor)
                        (concat (:lt.plugins.auto-complete/hints @editor) hints)
                        hints)))

(defn process-hint [hint]
  (if (string? hint)
    #js {:completion hint}
    (clj->js hint)))

(behavior ::update-hints
          :triggers #{:editor.julia.hints.update}
          :reaction (fn [editor {:keys [hints notextual pattern] :as res}]
                      (object/merge! editor {::hints (map process-hint hints)
                                             ::no-textual-hints notextual
                                             ::fresh-hints true
                                             :token-pattern (when pattern (js/RegExp. (str pattern "$")))})
                      (object/raise auto-complete/hinter :refresh!)))

(set! _get-token auto-complete/get-token)

(set! auto-complete/get-token
  (fn [ed pos]
    (if-let [pattern (@ed :token-pattern)]
      (let [line (-> ed (editor/line (:line pos)) (.substring 0 (:ch pos)))
            match (re-find pattern line)]
        {:start (- (count line) (count match))
         :end (:ch pos)
         :line (:line pos)
         :string match})
      (_get-token ed pos))))