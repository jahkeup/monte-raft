(ns monte-raft.node-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node :as node]
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

(deftest test-handle-message
  (testing "Calls handler when matches message"
    (let [called? (atom false)
          override-handlers {:ping #(reset! called? true)}]
      (with-redefs [handlers/cmd-handlers override-handlers]
        (node/handle-message "ping")
        (is called?)))))

(deftest test-on-message-reset!
  (testing "method should set value on channel message"
    (let [channel (chan)
          state-changed? (atom false)]
      (node/on-message-reset! channel state-changed? true)
      (>!! channel "hi")
      (is state-changed?))))

(deftest test-state-updates
  (testing "state-run should be updating transient-state on updates"
    (let [pub-remote "inproc://state-updates"
          stop-chan (chan) ; To stop the state thread
          expected-state {"name" "jake"}]
      (with-open
          [state-update-pub (socket/make-state-update-publisher socket/ctx
                              pub-remote)
           state-update-sub (socket/make-state-update-subscriber socket/ctx
                              pub-remote)]
        (zmq/subscribe state-update-sub "")
        (binding [node-state/transient-state (atom nil)]
          ;; Start a state management "thread"
          (let [finished-chan
                (go (node/state-run
                      {:update-socket state-update-sub
                       :stop-chan stop-chan
                       :check-period 1000}))]
            (socket/send-str-timeout state-update-pub socket/default-timeout
              (json/write-str expected-state))
            (>!! stop-chan true)
            (is (= (<!! finished-chan) :exiting))
            (is (= @node-state/transient-state
                  (json/write-str expected-state)))))))))


