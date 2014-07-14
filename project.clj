(defproject monte-raft "0.1.0-SNAPSHOT"
  :description "RAFT Implementation for MATH430"
  :url "https://github.com/jahkeup/monte-raft"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[quickie "0.2.5"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.zeromq/jeromq "0.3.3"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/data.json "0.2.5"]])
