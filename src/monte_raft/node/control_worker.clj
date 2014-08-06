(ns monte-raft.node.control-worker
  (:require [taoensso.timbre :as log]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.leader-worker :as leader]
            [monte-raft.node.state-worker :as state]
            [clojure.core.async :as async :refer [<!! >!! chan]]
            [zeromq.zmq :as zmq]))

(defn handle-simple-command
  "Inbound message dispatcher, msg should be raw message receieved
  from remotes"
  [reply-socket msg worker-config]
  (log/tracef "Handling message: '%s'" msg)
  (if-let [handler-func (and (msgs/valid-cmd? msg)
                          (get handlers/cmd-handlers (msgs/to-command-fmt msg)))]
    (do (handler-func reply-socket worker-config) :processed)
    :unprocessed))

(defn is-msg?
  "Predicate: is the message (first word of parts) the is-msg?"
  [is-msg parts]
  (let [lmsg (clojure.string/lower-case (first parts))
        lis-msg (clojure.string/lower-case is-msg)]
    (= lis-msg lmsg)))

(defn string-keyword
  "Create a keyword from a string like ':n1' => :n1 (instead of ::n1)
  which would normally result from (keyword ':n1')"
  [string]
  (if string
    (keyword (clojure.string/replace string #"^:" ""))))

(defn handle-message
  "Handle a command message from a remote"
  [reply-sock msg worker-config]
  (if (= :unprocessed (handle-simple-command reply-sock msg worker-config))
    (let [msg-parts (clojure.string/split msg #"\s+")]
      (log/tracef "Control worker is handling complex command: '%s'" msg)
      (cond
        (is-msg? "TERM" msg-parts)
        (if (= 2 (count msg-parts))
          (handlers/handle-term reply-sock
            (long (get msg-parts 1)) worker-config)
          (handlers/handle-term reply-sock worker-config))

        (is-msg? "ELECT" msg-parts)
        (handlers/handle-elect reply-sock
          (string-keyword (get msg-parts 1)) worker-config)

        (is-msg? "FOLLOW" msg-parts)
        (handlers/handle-follow reply-sock
          (string-keyword (get msg-parts 1)) worker-config)
        :default (handlers/handle-unknown-message reply-sock)))))

(defn maybe-handle-command-from
  "Handler delegation, dies on a timeout"
  [control-socket worker-config]
  (if-let [message (socket/receive-str-timeout control-socket)]
    (handle-message control-socket message worker-config)
    :timeout))

(defn terminate-workers
  "Send terminate to all workers for node"
  [{:keys [node-id kill-codes]}]
  (doall (for [w '(:state :leader)]
           (do (log/tracef "Control worker (%s) sending '%s' kill message using %s"
                 node-id (name w) (kill-codes w))
               (worker/signal-terminate (kill-codes w))))))

(defn maybe-start-leader
  "Start the leader-worker iff the node is the leader"
  [started-chan {:keys [node-id] :as config}]
  (if (leader/is-leader? config)
    (do (worker/start (leader/leader-worker config
                        started-chan))
        (log/debugf "Control (%s) is holding for leader.." node-id)
        (let [res (<!! started-chan)]
          (if (= res :leader-fail)
            (throw (Exception. "Leader worker failed to start.")))))))

(defn start-state-worker
  "Start the state worker. For use in control-worker"
  [started-chan {:keys [node-id] :as config}]
  (if (leader/leader-remote config)
    (do (worker/start (state/state-worker
                        config started-chan))
        (log/debugf "Control (%s) is holding for state.." node-id)
        (let [res (<!! started-chan)]
          (if (= res :state-fail)
            (throw (Exception. "State worker failed to start.")))))
    (worker/log-error-throw
      (format "Control worker (%s) cannot determine leader publishing remote or not given." node-id))))

(defn control-worker
  "Go thread control worker to process incoming messages, must be
  started prior to other workers. Will listen for commands/leader
  messages on 'control-binding'. Can be terminated with
  monte-raft.node.worker/signal-terminate, note: workers now listen on
  a node-config'd kill-code, so use that."
  ([worker-config]
     (control-worker worker-config nil))
  ([{:keys [node-id kill-codes publish-binding control-binding] :as worker-config} started-chan]
     (log/tracef "Control worker (%s) starting with config: \n%s" node-id
       (with-out-str (clojure.pprint/pprint worker-config)))
     (log/tracef "Starting control worker (%s): listening on %s" node-id control-binding)
     (try
       (let [worker-started-chan (chan 1)]
         (with-open [control-socket (doto (zmq/socket socket/ctx :rep)
                                      (zmq/bind control-binding))]
           (log/trace "Control worker started.")
           (maybe-start-leader worker-started-chan worker-config)
           (start-state-worker worker-started-chan worker-config)
           (and started-chan (>!! started-chan :control))
           ;; How about we just kill state and restart it!?  same
           ;; thing for the leader, if there's been a change just kill
           ;; them and start them again. I will need to ensure that
           ;; they've actually exited though..
           (worker/until-worker-terminate worker-config :control
             (maybe-handle-command-from control-socket worker-config))
           (log/trace "Control socket is preparing to exit.")))
       (catch Throwable e (do (clojure.stacktrace/print-cause-trace e)
                              (log/errorf "Control (%s) encountered a serious error." node-id)
                              (and started-chan (>!! started-chan :control-fail))))
       (finally (terminate-workers worker-config)))
     (log/infof "Control worker (%s) exiting." node-id)
     :terminated))

