(ns monte-raft.node.macros
  (:require [clojure.core.async :refer [go <! chan]]))

(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking"
  [channel set-atom set-value]
  `(go (do (<! ~channel)
           (reset! ~set-atom ~set-value))))

(defmacro until-message-from [message-chan & body]
  `(let [should-stop# (atom false)]
     (on-message-reset! ~message-chan should-stop# true)
     (while (not @should-stop#)
       ~@body)))
