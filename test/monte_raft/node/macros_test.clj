(ns monte-raft.node.macros-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :as macros]
            [zeromq.zmq :as zmq]
            [clojure.core.async :as async :refer [go chan >!! <!!]]))

(deftest test-on-message-reset!
  (testing "method should set value on channel message"
    (let [channel (chan)
          state-changed? (atom false)]
      (macros/on-message-reset! channel state-changed? true)
      (>!! channel "hi")
      (Thread/sleep 1)
      (is (boolean (true? @state-changed?))))))

