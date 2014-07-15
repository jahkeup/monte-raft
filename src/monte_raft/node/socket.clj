(ns monte-raft.node.socket
  (:require [zeromq.zmq :as zmq])
  (:import [org.zeromq ZMQ$Socket]))

(defonce ctx (zmq/context))

;; These should be bound in the context that they are being used, see
;; docstring for purpose
(def ^:dynamic node-control-socket
  "Node - Control socket listening on node for control commands from
  the leader")
(def ^:dynamic control-socket
  "Node - Control socket used by the leader for communicating with
  cluster hosts.")
(def ^:dynamic state-pub-socket
  "Leader - socket for publishing any state changes")
(def ^:dynamic state-sub-socket
  "Node - socket that listens for state changes. Will also receive ")

(defn make-heartbeat-listener
  "Make a heartbeat listener will use existing context and binding to
  connect. returns a socket (which *must* be closed by the utilizing
  side."
  [context binding]
  (doto (zmq/socket context :rep)
    (zmq/bind binding)))

(defn make-leader-connector
  "Make a heartbeat connector with context that connects to the remote"
  [context remote]
  (doto (zmq/socket context :req)
    (zmq/connect remote)))

(defn make-state-update-publisher
  "Make a publisher socket for sending out state changes"
  [context binding]
  (doto (zmq/socket context :pub)
    (zmq/bind binding)))

(defn make-state-update-subscriber
  "Make a subscriber connection to the leader to recieve changes of state."
  [context leader-remote]
  (doto (zmq/socket context :sub)
    (zmq/connect leader-remote)))

(defn receive-str-timeout
  "Receive string from socket with timeout using setsockopt to use
  timeout"
  [^ZMQ$Socket socket timeout]
  (let [socktimeout (.getReceiveTimeOut socket)
        _ (.setReceiveTimeOut socket timeout)
        msg (zmq/receive-str socket)
        _ (.setReceiveTimeOut socket (int socktimeout))]
    (if msg msg nil)))

