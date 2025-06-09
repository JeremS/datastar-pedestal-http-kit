(ns bench.broadcast
  (:require
    [fr.jeremyschoffen.pedestal.datastar.http-kit :as d*hk]
    [io.pedestal.connector                        :as conn]
    [io.pedestal.http.http-kit                    :as phk]
    [starfederation.datastar.clojure.api          :as d*]))


(defonce !conns (atom #{}))


(defn handler [req]
  (d*hk/->sse-response req
    {d*hk/on-open
     (fn [sse]
       (swap! !conns conj sse))

     d*hk/on-close
     (fn [sse _status]
       (println "disconnecting!!!")
       (swap! !conns disj sse))}))


(defn broadcast [f]
  (doseq [conn @!conns]
    (f conn)))


(def routes
  #{["/" :get #'handler :route-name :home]})


(defn ->connector []
  (-> (conn/default-connector-map 8080)
      (conn/with-default-interceptors)
      (conn/with-routes routes)
      (phk/create-connector nil)))


(defonce !server (atom nil))


(defn stop! []
  (when-let [server @!server]
    (conn/stop! server)))


(defn start! []
  (stop!)
  (reset! !server (conn/start! (->connector))))


(comment
  ;; Start the connector
  (start!)
  ;; Use curl to connect 1 to n processes
  ;; curl -vv http://localhost:8080

  ;; Broadcast Datastar events
  (broadcast #(d*/merge-fragment! % "toto"))
  (broadcast #(d*/execute-script! % "console.log('titi')"))

  ;; You can close SSE generators from here or CTRL-c in the terminal
  (-> !conns deref first d*/close-sse!)

  (stop!))

