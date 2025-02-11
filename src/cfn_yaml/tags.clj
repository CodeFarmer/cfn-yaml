(ns cfn-yaml.tags
  (:require [clojure.string :as str]
            [clj-yaml.core :as yaml])
  (:import (org.yaml.snakeyaml.nodes ScalarNode SequenceNode MappingNode NodeTuple Tag NodeId)
           (org.yaml.snakeyaml DumperOptions$ScalarStyle DumperOptions$FlowStyle)
           (org.yaml.snakeyaml.constructor Construct Constructor)
           (org.yaml.snakeyaml.representer Represent)))

(defrecord !Sub [string bindings]
  yaml/YAMLCodec
  (decode [data keywords]
    data)
  (encode [data]
    data))

(defrecord !Ref [logicalName]
  yaml/YAMLCodec
  (decode [data keywords]
    data)
  (encode [data]
    data))

(defrecord !GetAtt [logicalName]
  yaml/YAMLCodec
  (decode [data keywords]
    data)
  (encode [data]
    data))

(defrecord !Cidr [ipBlock count cidrBits]
  yaml/YAMLCodec
  (decode [data keywords]
    data)
  (encode [data]
    data))

(defrecord !Base64 [valueToEncode]
  yaml/YAMLCodec
  (decode [data keywords]
    data)
  (encode [data]
    data))

(defn constructors [get-constructor]
  (let [construct #(.construct (get-constructor %) %)]
    (->> [[!Ref #(->!Ref (.getValue %))]
          [!GetAtt #(->!GetAtt (.getValue %))]
          [!Sub (fn [node]
                  (if (= NodeId/scalar (.getNodeId node))
                    (->!Sub (.getValue node) {})
                    (->!Sub (-> node .getValue first .getValue)
                            (into {}
                                  (map #(do [(-> % .getKeyNode .getValue) (construct (.getValueNode %))]))
                                  (-> node .getValue second .getValue)))))]
          [!Cidr (fn [node] (apply ->!Cidr (map construct (.getValue node))))]
          [!Base64 (fn [node]
                     (condp = (.getNodeId node)
                       NodeId/scalar (->!Base64 (.getValue node))
                       NodeId/mapping (->!Base64 (into {}
                                                       (map (fn [node-tuple]
                                                              [(construct (.getKeyNode node-tuple))
                                                               (construct (.getValueNode node-tuple))]))
                                                       (.getValue node)))))]]
         (into {} (map (fn [[klass f]]
                         [(Tag. (.getSimpleName klass)) (reify org.yaml.snakeyaml.constructor.Construct
                                                          (construct [this node]
                                                            (f node)))]))))))

(defn scalar-node [tag value & {:keys [style] :or {style DumperOptions$ScalarStyle/PLAIN}}]
  (ScalarNode. (if (string? tag)
                 (Tag. tag)
                 tag)
               value
               nil
               nil
               style))

(defn representers [represent-data]
  (let [represent-map (fn [m & {:keys [tag] :or {tag Tag/MAP}}]
                        (MappingNode. tag
                                      (for [[k v] m]
                                        (NodeTuple. (represent-data k) (represent-data v)))
                                      DumperOptions$FlowStyle/BLOCK))]
    (->> [[!Ref #(scalar-node "!Ref" (:logicalName %))]
          [!GetAtt #(scalar-node "!GetAtt" (:logicalName %))]
          [!Sub (fn [{:keys [string bindings]}]
                  (if (empty? bindings)
                    (scalar-node "!Sub" string :style (if (.contains string "\n")
                                                               DumperOptions$ScalarStyle/LITERAL
                                                               DumperOptions$ScalarStyle/PLAIN))
                    (SequenceNode. (Tag. "!Sub")
                                   [(scalar-node Tag/STR string) (represent-map bindings)]
                                   DumperOptions$FlowStyle/BLOCK)))]
          [!Cidr #(SequenceNode. (Tag. "!Cidr")
                                 [(scalar-node Tag/STR (:ipBlock %) :style DumperOptions$ScalarStyle/DOUBLE_QUOTED)
                                  (scalar-node Tag/INT (str (:count %)))
                                  (scalar-node Tag/INT (str (:cidrBits %)))]
                                 DumperOptions$FlowStyle/FLOW)]
          [!Base64 (fn [{:keys [valueToEncode]}]
                     (cond
                       (string? valueToEncode) (scalar-node "!Base64" valueToEncode)
                       (map? valueToEncode) (represent-map valueToEncode :tag (Tag. "!Base64"))))]]
         (into {} (map (fn [[klass f]]
                         [klass (reify org.yaml.snakeyaml.representer.Represent
                                  (representData [this data]
                                    (f data)))]))))))
