(ns monte-raft.node.state-worker-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [monte-raft.node.state-worker :as state]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.leader-worker :refer [publish-state]]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go chan >!! <!!]]
            [monte-raft.test.worker-macros :refer :all]))

(deftest-worker test-state-worker-updates
  (testing "state-worker should directly update transient"
    (let [expected-state {"name" "jake"}
          expected-encoded (json/write-str expected-state)
          worker-config (assoc (node-config)
                          :leader-publish-remote ((node-config) :publish-binding))]
      (with-open [update-pub (doto (zmq/socket socket/ctx :pub)
                               (zmq/bind ((node-config) :publish-binding)))]
        (let [transient-state (get-in worker-config [:state :transient])
              running-worker (worker/start
                               (state/state-worker worker-config))]
          (wait-do 100
            (publish-state update-pub expected-encoded)
            (worker/signal-terminate :state worker-config)
            (is (= (<!! running-worker) :terminated))
            (is (= @transient-state expected-encoded))))))))
