(ns monte-raft.client
  (:require [clojure.data.json :as json]
            [zeromq.zmq :as zmq]
            [monte-raft.node.socket :as socket]))
;; Client interaction suite

(def example-state {:name "Jake" :class "MATH430"})

(defn encode "Encode a state object (json wrapper)"
  [state]
  (json/write-str state))

(defn decode "Decode a state object from leader"
  [state-str]
  (json/read-str state-str))

(defn send-command
  ([client-socket command]
     (send-command client-socket command socket/default-timeout))
  ([client-socket command timeout]
     (socket/send-str-timeout client-socket command timeout)))

(defmacro with-client [client-sym leader-remote & body]
  `(with-open [~(symbol client-sym)
               (doto (zmq/socket socket/ctx :req)
                     (zmq/connect ~leader-remote))]
     ~@body))

(defn update-state-msg [state]
  (format "UPDATE %s" state))

(defn update-state [leader-remote new-state]
  (let [encoded-state (encode new-state)]
    (with-client client leader-remote
      (send-command client (update-state-msg encoded-state)))))

(defn get-state [leader-remote]
  (with-client client leader-remote
    (let [response (send-command client "GET")]
      (decode response))))

