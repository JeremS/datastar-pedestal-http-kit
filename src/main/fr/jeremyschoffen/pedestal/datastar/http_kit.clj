(ns fr.jeremyschoffen.pedestal.datastar.http-kit
  (:require
    [clojure.core.async :refer [<! go]]
    [org.httpkit.server :as hk-server]
    [starfederation.datastar.clojure.adapter.common :as ac]
    [starfederation.datastar.clojure.adapter.http-kit.impl :as impl]
    [starfederation.datastar.clojure.utils :refer [def-clone]]))

;; Modified version of https://github.com/JeremS/datastar/blob/develop/sdk/clojure/adapter-http-kit/src/main/starfederation/datastar/clojure/adapter/http_kit.clj
;; adapted for pedestal.

(def-clone on-open ac/on-open)
(def-clone on-close ac/on-close)
(def-clone on-exception ac/on-exception)
(def-clone default-on-exception ac/default-on-exception)


(def-clone write-profile ac/write-profile)

(def-clone basic-profile                impl/basic-profile)
(def-clone buffered-writer-profile      ac/buffered-writer-profile)
(def-clone gzip-profile                 ac/gzip-profile)
(def-clone gzip-buffered-writer-profile ac/gzip-buffered-writer-profile)


(def commited-ch-key :io.pedestal.http.request/response-commited-ch)


(defn ->sse-response
  "Make a Ring like response that will start a SSE stream.

  The status code and the the SSE specific headers are sent automatically
  before [[on-open]] is called.

  Note that the SSE connection stays opened util you close it.

  General options:
  - `:status`: status for the HTTP response, defaults to 200.
  - `:headers`: ring headers map to add to the response.
  - [[on-open]]: mandatory callback called when the generator is ready to send.
  - [[on-close]]: callback called when the underlying Http-kit AsyncChannel is
    closed. It receives a second argument, the `:status-code` value we get from
    the closing AsyncChannel.
  - [[on-exception]]: callback called when sending a SSE event throws.
  - [[write-profile]]: write profile for the connection.
    Defaults to [[basic-profile]]

  SDK provided write profiles:
  - [[basic-profile]]
  - [[buffered-writer-profile]]
  - [[gzip-profile]]
  - [[gzip-buffered-writer-profile]]

  You can also take a look at the `starfederation.datastar.clojure.adapter.common`
  namespace if you want to write your own profiles.
  "
  [ring-request {:keys [status] :as opts}]
  {:pre [(ac/on-open opts)]}
  (let [commited-ch (commited-ch-key ring-request)
        on-open-cb (ac/on-open opts)
        on-close-cb (ac/on-close opts)
        future-send! (promise)
        future-gen (promise)]
    (assoc
      (hk-server/as-channel ring-request
        {:on-open
         (fn [ch]
           (let [send! (impl/->send! ch opts)
                 sse-gen (impl/->sse-gen ch send! opts)]
             (deliver future-gen sse-gen)
             (deliver future-send! send!)
 
             (go
               (<! commited-ch)
               (on-open-cb sse-gen))))

         :on-close
         (fn [_ status]
           (let [closing-res
                 (ac/close-sse!
                  #(when-let [send! (deref future-send! 0 nil)] (send!))
                  #(when on-close-cb
                     (on-close-cb (deref future-gen 0 nil) status)))]
             (if (instance? Exception closing-res)
               (throw closing-res)
               closing-res)))})
      :status (or status 200)
      :headers (ac/headers ring-request opts))))

