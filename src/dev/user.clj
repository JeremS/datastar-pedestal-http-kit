(ns user
  (:require
    [clojure.repl.deps :as crdeps]
    [clj-reload.core :as reload]))


(alter-var-root #'*warn-on-reflection* (constantly true))



(reload/init
  {:no-reload ['user]})


(defn reload! []
  (reload/reload))


(defn clear-terminal! []
  (binding [*out* (java.io.PrintWriter. System/out)]
    (print "\033c")
    (flush)))


(comment
  (reload!)
  (crdeps/sync-deps))

