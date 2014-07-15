(ns monte-raft.node.socket
  (:require [zeromq.zmq :as zmq]
            [monte-raft.node.messaging :as msgs])
  (:import [org.zeromq ZMQ$Socket]))

(defonce ctx (zmq/context))

;; These should be bound in the context that they are being used, see
;; docstring for purpose
(def ^:dynamic node-control-socket
  "Node - Control socket listening on node for control commands from
  the leader" nil)
(def ^:dynamic control-socket
  "Node - Control socket used by the leader for communicating with
  cluster hosts." nil)
(def ^:dynamic state-pub-socket
  "Leader - socket for publishing any state changes" nil)
(def ^:dynamic state-sub-socket
  "Node - socket that listens for state changes. Will also receive " nil)

(def default-timeout "Default timeout to use on sockets" 2000)

(defn make-control-listener
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


(defn or-default-timeout
  "Return either the default timeout or the timeout specified if not nil"
  ([] default-timeout)
  ([timeout] (if (and (integer? timeout)
                   (> 300 timeout)) timeout default-timeout)))

(defmacro with-zmq-timeout
  "Use a timeout on the socket to make a request and then reset to
  previous value"
  [socket timeout & body]
  `(let [socktimeout# (.getReceiveTimeOut ~socket)
         _# (.setReceiveTimeOut ~socket ~timeout)
         result# (do ~@body)
         _# (.setReceiveTimeOut ~socket (int socktimeout#))]
     result#))

(defn send-str-timeout
  "Send string with a timeout, will return true or false on send (from ZMQ$Socket#send)"
  [^ZMQ$Socket socket timeout send-str]
  (with-zmq-timeout socket timeout
    (if (or (msgs/valid-cmd? send-str) (msgs/valid-response? send-str))
      (let [send-str (msgs/command-to-str send-str)]
        (zmq/send-str socket send-str))
      (zmq/send-str socket send-str))))

(defn receive-str-timeout
  "Receive string from socket with timeout using setsockopt to use
  timeout"
  [^ZMQ$Socket socket timeout]
  (with-zmq-timeout socket timeout
    (zmq/receive-str socket)))

