(ns monte-raft.servers
  (:require [monte-raft.node.socket :as socket]
            [zeromq.zmq :as zmq]))

(defn simple-publisher [binding message]
  (println "Listening on" binding)
    (println "Will be sending message" message "repeatedly")
    (with-open [context (zmq/context)
                publisher (socket/make-state-update-publisher context binding)]
      (while true
        (println "SEND:" message)
        (zmq/send-str publisher message)
        (Thread/sleep 2000))))

(defn simple-subscriber [remote]
  (println "Subscribing to" remote)
  (with-open [context (zmq/context)
              subscriber (socket/make-state-update-subscriber context remote)
              _ (zmq/subscribe subscriber "")]
    (while true
      (println "Waiting for message..")
      (if-let [msg (socket/receive-str-timeout subscriber 2000)]
        (println "Recieved: " msg)
        (println "Timed out.. ")))))
