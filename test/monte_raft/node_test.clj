(ns monte-raft.node-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node :as node]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go chan >!! <!!]]))

(deftest test-elect!
  (testing "opens new socket and connects to cluster nodes"
    (let [fake-node "inproc://fake-node"]
     (with-open [sock (doto (zmq/socket socket/ctx :rep)
                        (zmq/bind fake-node))]
       (binding [node-state/cluster [fake-node]]
         (go (node/elect!))
         (is (= (socket/receive-str-timeout sock 3000)
               (msgs/command-to-str :elect))))))))

