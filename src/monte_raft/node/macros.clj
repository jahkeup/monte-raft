(ns monte-raft.node.macros
  (:require [clojure.core.async :refer [go <!]]))


(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking"
  [channel set-atom set-value]
  `(go (do (<! ~channel)
        (reset! ~set-atom ~set-value))))

