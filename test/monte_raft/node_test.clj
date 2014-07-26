(ns monte-raft.node-test
  (:require [clojure.test :refer :all]
            [monte-raft.test.worker-macros :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.worker :as worker]
            [monte-raft.node :as node]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go chan >!! <!!]]
            [taoensso.timbre :as log]))

(deftest-node test-node-starts
  (log/with-log-level :trace
    (let [running-node (worker/start
                        (node/node (node-config)))]
     (wait-do 1000
       (worker/signal-terminate (get-in (node-config) [:kill-codes :control]))
       (is (= (<!! running-node) :terminated))))))

