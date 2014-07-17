(ns monte-raft.client
  (:require [clojure.data.json :as json]
            [zeromq.zmq :as zmq]
            [monte-raft.node.socket :as socket]))
;; Client interaction suite

(def example-state "Simple internal representation of the state that
  will be maintained by the raft cluster"
  {:name "Jake" :class "MATH430"})

(defn encode
  "Encode a state object (json wrapper)"
  [state]
  (json/write-str state))

(defn decode
  "Decode a state object from leader"
  [state-str]
  (json/read-str state-str))

(defn send-command
  "Send command to system, if timeout is not given the
  node.socket/default-timeout will be used."
  ([client-socket command]
     (send-command client-socket command socket/default-timeout))
  ([client-socket command timeout]
     (socket/send-str-timeout client-socket command timeout)))

(defmacro with-client
  "Create a client socket bound on `client-sym'. Will connect to
  leader, exec, and then close socket."
  [client-sym leader-remote & body]
  `(with-open [~(symbol client-sym)
               (doto (zmq/socket socket/ctx :req)
                     (zmq/connect ~leader-remote))]
     ~@body))

(defn update-state-msg
  "Format an update message using the state object that should already
  be represented by a json string"
  [state]
  (if (string? state)
    (format "UPDATE %s" state)
    (format "UPDATE %s" (encode state))))

(defn update-state
  "Update the system consensus state to `new-state'"
  [leader-remote new-state]
  (with-client client leader-remote
    (send-command client (update-state-msg new-state))))

(defn get-state
  "Get the current state held in the system"
  [leader-remote]
  (with-client client leader-remote
    (let [response (send-command client "GET")]
      (decode response))))

