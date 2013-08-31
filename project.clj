(defproject gulping-queue "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :plugins [[lein-midje "3.1.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}}
  :alias {"midje" ["with-profile" "dev" "midje"]})

