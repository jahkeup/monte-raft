(ns monte-raft.node.macros
  (:require [clojure.core.async :refer [go <! chan]]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking"
  [channel set-atom set-value]
  `(go (do (log/trace "Waiting for channel lock message")
           (<! ~channel)
           (log/trace "Received unlock message")
           (reset! ~set-atom ~set-value))))

(defmacro on-receive-reset!
  "Upon receiving a message from a zmq socket, reset! the `set-atom'
  to `set-value'"
  [socket set-atom set-value]
  `(go (do (log/trace "Waiting for socket unlock message on socket")
           (zmq/receive-str ~socket)
           (log/trace "Received message on socket")
           (reset! ~set-atom ~set-value))))

(defmacro until-message-from [message-chan & body]
  `(let [should-stop# (atom false)]
     (log/trace "Scheduling unlock on message")
     (on-message-reset! ~message-chan should-stop# true)
     (let [yield# (while (not @should-stop#)
                    ~@body)]
       (log/trace "Unblocked. Exiting.")
       yield#)))

(defmacro until-receive-from [socket & body]
  `(let [should-stop# (atom false)]
     (log/trace "Scheduling unlock on receive")
     (on-receive-reset! ~socket should-stop# true)
     (let [yield# (while (not @should-stop#)
                    ~@body)]
       (log/trace "Unblocked. Exiting.")
       yield#)))

