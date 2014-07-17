(ns monte-raft.node.macros
  (:require [clojure.core.async :refer [go <! chan]]
            [zeromq.zmq :as zmq]))

(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking"
  [channel set-atom set-value]
  `(go (do (<! ~channel)
           (reset! ~set-atom ~set-value))))

(defmacro on-receive-reset!
  "Upon receiving a message from a zmq socket, reset! the `set-atom'
  to `set-value'"
  [socket set-atom set-value]
  `(go (do (zmq/receive-str ~socket)
           (reset! ~set-atom ~set-value))))

(defmacro until-message-from [message-chan & body]
  `(let [should-stop# (atom false)]
     (on-message-reset! ~message-chan should-stop# true)
     (while (not @should-stop#)
       ~@body)))

(defmacro until-receive-from [socket & body]
  `(let [should-stop# (atom false)]
    (on-receive-reset! ~socket should-stop# true)
    (while (not @should-stop#)
      ~@body)))

