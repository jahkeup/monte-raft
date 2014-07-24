(ns monte-raft.test.worker-macros
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!! <! >! >!! chan]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.macros :refer :all]
            [taoensso.timbre :as log]))

(defmacro binding-with-node-id [node-id & body]
  `(binding [node-state/node-id ~node-id]
       (with-open [comm-sock# (worker/make-comm-sock)]
         (binding [worker/comm-sock comm-sock#]
           (with-redefs [node-state/cluster (atom (node-state/-make-node-cluster-map '(~node-id)))]
             ~@body)))))

(defn node-config []
  (@node-state/cluster (keyword (format "node-%s" (name node-state/node-id)))))

(defmacro deftest-worker
  "deftest for worker where bindings have already been made for
  testing isolated worker(s)."
  [test-name & body]
  `(deftest ~test-name
     (binding-with-node-id "test-node" ~@body)))

(defalias deftest-node deftest-worker)

(defmacro with-messages-logged [& body]
  `(do (go (worker/comm-sock-logger (worker/comm-remote)))
       (let [res# (do ~@body)]
         (worker/signal-terminate :logger)
         res#)))

(defmacro wait-do
  "Wait a wait-time and then execute body"
  [wait-time & body]
  `(do (Thread/sleep ~wait-time)
       ~@body))


