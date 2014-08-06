(ns lt.objs.langs.julia.profiler
  (:require [lt.objs.langs.julia.util :as util]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.util.dom :as dom]
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

;; CodeMirror extension

(behavior ::render-bars
          :triggers #{:object.instant}
          :reaction (fn [editor]
                      (editor/on editor :renderLine render-reaction-wrapper)))

(defn render-reaction-wrapper [& args]
  (apply render-bar args))

(defn render-bar [cm line dom]
  (when-let [percent (.-percent line)]
    (dom/append dom
      (crate/html [:div.progress {:style (str "width:" percent)}]))))

;; Usage similar to Highlights

(defn % [x] (str (* x 100) "%"))

(defn editor-for-file [file]
  (first (pool/by-path file)))

(def lines (atom #{}))

(defn refresh-line [{:keys [file line percent handle] :as l}]
  (if handle
    l
    (if-let [ed (editor-for-file file)]
      (let [handle (editor/line-handle ed (dec line))]
        (when handle (set! (.-percent handle) (% percent)))
        (let [l (assoc l :handle handle :ed ed)]
          (swap! lines conj l)
          l))
      l)))

(defn refresh-lines [lines]
  (into #{} (map refresh-line lines)))

(defn editors [lines]
  (into #{} (map :ed lines)))

(defn refresh-and-update [lines]
  (let [lines (refresh-lines lines)]
    (doseq [ed (editors lines)]
      (editor/refresh ed))
    lines))

(defn dom-bars [] (dom/$$ ".CodeMirror-code .progress"))

(defn clear []
  (doseq [{handle :handle} @lines]
    (set! (.-percent handle) nil))
  (reset! lines #{})
  (doseq [bar (dom-bars)]
    (.remove bar)))

(defn callback [f]
  (js/setTimeout f 0))

(defn animate-in []
  (let [bars (dom-bars)]
    (doseq [bar bars]
      (dom/remove-class bar :animated)
      (dom/add-class bar :hidden))
    (callback ; Hack to make the animation work
     #(doseq [bar bars]
        (dom/add-class bar :animated)
        (dom/remove-class bar :hidden)))))

(defn animate-out []
  (doseq [bar (dom-bars)]
    (dom/add-class bar :hidden)))

(defn set-lines [lines]
  (clear)
  (refresh-and-update lines)
  (animate-in))

(defn clear-lines []
  (animate-out)
  (js/setTimeout clear 200))
