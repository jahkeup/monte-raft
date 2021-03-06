(ns monte-raft.test.worker-macros
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!! <! >! >!! chan]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.macros :refer :all]
            [taoensso.timbre :as log]))

(defn node-config []
  (@node-state/cluster (keyword node-state/node-id)))

(defmacro binding-with-default-cluster [node-id leader-id & body]
  `(with-redefs
     [node-state/cluster
      (atom (assoc-in (node-state/-make-node-cluster-map '(~node-id))
              [(keyword ~node-id) :state :leader-id] (atom (keyword ~leader-id))))]
     (worker/with-comm-sock (node-config)
       ~@body)))

(defmacro binding-with-node-id
  [node-id leader-id & body]
  `(binding [node-state/node-id ~node-id]
     (binding-with-default-cluster ~node-id ~leader-id
       ~@body)))

(defmacro deftest-worker
  "deftest for worker where bindings have already been made for
  testing isolated worker(s)."
  [test-name & body]
  `(deftest ~test-name
     (binding-with-node-id "test-node" "test-node" ~@body)))

(defalias deftest-node deftest-worker)

(defmacro with-messages-logged [worker-config & body]
  `(do (go (worker/comm-sock-logger (worker/comm-remote ~worker-config)))
       (log/tracef "!!!!!!! Bound worker-comm message logger. !!!!!!!!!")
       (let [res# (do ~@body)]
         (worker/signal-terminate :logger)
         res#)))

(defmacro wait-do
  "Wait a wait-time and then execute body"
  [wait-time & body]
  `(do (Thread/sleep ~wait-time)
       ~@body))


(def bar "\n================================================================================")
