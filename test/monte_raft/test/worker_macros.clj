(ns monte-raft.test.worker-macros
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!! <! >! >!! chan]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [taoensso.timbre :as log]))

(defmacro deftest-worker
  "deftest for worker where bindings have already been made for
  testing isolated worker(s)."
  [test-name & body]
  `(deftest ~test-name
     (binding [node-state/node-id "test-node"]
       (with-open [comm-sock# (worker/make-comm-sock)]
         (binding [worker/comm-sock comm-sock#]
           ~@body)))))


(defmacro wait-do
  "Wait a wait-time and then execute body"
  [wait-time & body]
  `(do (Thread/sleep ~wait-time)
       ~@body))

