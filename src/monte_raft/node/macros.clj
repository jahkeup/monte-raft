(ns monte-raft.node.macros
  (:require [clojure.core.async :refer [go <! chan]]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking, the resulting channel (from go) will pass
  along the received value and exit."
  [channel set-atom set-value]
  `(go (log/trace "Waiting for channel unlock message")
       (let [received# (<! ~channel)]
         (log/tracef "Received unlock message: '%s'" received#)
         (reset! ~set-atom ~set-value)
         received#)))

(defmacro on-receive-reset!
  "Upon receiving a message from a zmq socket, reset! the `set-atom'
  to `set-value', the resulting channel (from go) will pass along the
  received value and exit"
  [socket set-atom set-value]
  `(go (log/trace "Waiting for socket unlock message on socket")
       (let [received# (zmq/receive-str ~socket)]
         (log/tracef "Received message on socket: '%s'" received#)
         (reset! ~set-atom ~set-value)
         received#)))

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


;; https://github.com/sritchie/jackknife/blob/03bbd8584878914d28b7ad31225700c3ed306b4c/src/jackknife/def.clj#L22-L38
(defmacro defalias
  "Defines an alias for a var: a new var with the same root binding (if
  any) and similar metadata. The metadata of the alias is its initial
  metadata (as provided by def) merged into the metadata of the original."
  ([name orig]
     `(do
        (alter-meta!
         (if (.hasRoot (var ~orig))
           (def ~name (.getRawRoot (var ~orig)))
           (def ~name))
         ;; When copying metadata, disregard {:macro false}.
         ;; Workaround for http://www.assembla.com/spaces/clojure/tickets/273
         #(conj (dissoc % :macro)
                (apply dissoc (meta (var ~orig)) (remove #{:macro} (keys %)))))
        (var ~name)))
  ([name orig doc]
     (list `defalias (with-meta name (assoc (meta name) :doc doc)) orig)))
