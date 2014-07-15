(ns monte-raft.node
  (:require [monte-raft.node.socket :as socket]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [clojure.core.async :as async :refer [chan close! >! >!! <! <!! go go-loop]]))

(defn handle-message [msg]
  (if (msgs/valid-cmd? msg)
    (let [cmd (msgs/to-command-fmt msg)]
      (if (contains? msgs/cmd-handlers )))))

(defn node-run [{:keys [term leader-change-chan dead-time] :as opts}]
  (loop [term-change false]
    (while (not term-change)
      (if-let [cmd (msgs/to-command-fmt (socket/receive-str-timeout socket/control-socket dead-time))]))))

(defmacro on-message-set [channel set-atom set-value]
  `(do (<! ~channel)
      (reset! ~set-atom ~set-value)))

(defn state-run [{:keys [update-socket stop-chan check-period]}]
  (let [should-stop (atom false)]
    (go (on-message-set stop-chan should-stop true))
    (while (not should-stop)
      ;; Potential problems here in the future if the windows don't align..
      (if-let [new-state (socket/receive-str-timeout update-socket check-period)]
        (reset! node-state/transient-state new-state)))))

(defn node
  "Create a fully functional node, must provide a node-id, context,
  and binding for the control socket (eg: 'ipc:///tmp/node-id.sock' or
  'tcp://*:9001'"
  [node-id context binding]
  (binding [socket/node-control-socket (socket/make-control-listener context binding)
            node-state/state (atom nil)
            node-state/transient-state (atom nil)]
    (loop [term (atom 0)]
      ;; We're going to block on receiving any leader-change
      ;; messages. Upon leader change we'll also want to reconnect to
      ;; the leader properly.
      (let [leader-change (chan)]
        (node-run {:term term
                   :leader-change-chan leader-change})
        (swap! term inc)))))


