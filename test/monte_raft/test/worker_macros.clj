(ns monte-raft.test.worker-macros
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!! <! >! >!! chan]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [taoensso.timbre :as log]))

(defmacro deftest-worker [test-name & body]
  `(deftest ~test-name
     (binding [node-state/node-id "test-node"]
       (with-open [comm-sock# (worker/make-comm-sock)]
         (binding [worker/comm-sock comm-sock#]
           ~@body)))))


