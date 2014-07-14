(ns monte-raft.node.socket
  (:require [zeromq.zmq :as zmq])
  (:import [org.zeromq ZMQ$Socket]))

(defonce program-ctx (zmq/context))

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

