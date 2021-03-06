package edu.vanderbilt.chuilian.types;

import com.google.flatbuffers.FlatBufferBuilder;
import edu.vanderbilt.chuilian.loadbalancer.plan.ChannelMapping;
import edu.vanderbilt.chuilian.loadbalancer.plan.ChannelPlan;
import edu.vanderbilt.chuilian.loadbalancer.plan.Plan;
import edu.vanderbilt.chuilian.loadbalancer.plan.Strategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Killian on 6/2/17.
 */
public class TypesPlanHelper {
    private static final Logger logger = LogManager.getLogger(TypesPlanHelper.class.getName());
    public static byte[] serialize(Plan plan, long timeTag) {
        FlatBufferBuilder builder = new FlatBufferBuilder(2048);
        ChannelMapping channelMapping = plan.getChannelMapping();
        int[] channelPlans = null;
        int channelMappingOffset = 0;
        if (channelMapping != null && channelMapping.size() != 0) {
            // build channel mapping iff channel mapping is not empty
            channelPlans = new int[channelMapping.size()];
            int counterChannel = 0;
            for (Map.Entry<String, ChannelPlan> entry : channelMapping.entrySet()) {
                int topicOffset = builder.createString(entry.getValue().getTopic());
                int strategyOffset = builder.createString(entry.getValue().getStrategy().toString());
                int[] availableBroker = new int[entry.getValue().getAvailableBroker().size()];
                int counterBroker = 0;
                for (String curBrokerID : entry.getValue().getAvailableBroker()) {
                    availableBroker[counterBroker++] = builder.createString(curBrokerID);
                }
                int availableBrokerOffset = TypesChannelPlan.createAvailableBrokerVector(builder, availableBroker);
                channelPlans[counterChannel++] = TypesChannelPlan.createTypesChannelPlan(builder, topicOffset, strategyOffset, availableBrokerOffset);
            }
            channelMappingOffset = TypesPlan.createChannelMappingVector(builder, channelPlans);
        }
        TypesPlan.startTypesPlan(builder);
        TypesPlan.addVersion(builder, plan.getVersion());
        TypesPlan.addTimeTag(builder, timeTag);
        if (channelPlans != null) TypesPlan.addChannelMapping(builder, channelMappingOffset);
        int Plan = TypesPlan.endTypesPlan(builder);
        builder.finish(Plan);
        ByteBuffer buf = builder.dataBuffer();
        return builder.sizedByteArray();
    }

    public static TypesPlan deserialize(byte[] data) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
        return TypesPlan.getRootAsTypesPlan(buf);
    }

    public static Plan toPlan(TypesPlan typesPlan) {
        //logger.info("here-1");
        ChannelMapping channelMapping = new ChannelMapping();
        long version = typesPlan.version();
        int numChannel = typesPlan.channelMappingLength();
        //logger.info("here-2, numChannel: {}", numChannel);
        for (int i = 0; i < numChannel; i++) {
            //logger.info("here-3-0");
            String topic = typesPlan.channelMapping(i).topic();
            //logger.info("here-3-topic-{}", topic);
            String strategyString = typesPlan.channelMapping(i).strategy();
            //logger.info("here-3-strategyString-{}", strategyString);
            Strategy strategy = null;
            //logger.info("here-3-1, strategyString: {}", strategyString);
            switch (strategyString) {
                case "HASH":
                    strategy = Strategy.HASH;
                    break;
                case "ALL_SUB":
                    strategy = Strategy.ALL_SUB;
                    break;
                case "ALL_PUB":
                    strategy = Strategy.ALL_PUB;
            }
            //logger.info("here-3-2, strategy: {}", strategy.toString());
            Set<String> brokerIDs = new HashSet<>();
            int numBrokers = typesPlan.channelMapping(i).availableBrokerLength();
            //logger.info("here-3-3, numBrokers: {}", numBrokers);
            for (int j = 0; j < numBrokers; j++) {
                brokerIDs.add(typesPlan.channelMapping(i).availableBroker(j));
            }
            //logger.info("here-3-4");
            ChannelPlan channelPlan = new ChannelPlan(topic, brokerIDs, strategy);
            //logger.info("here-3-5");
            channelMapping.addNewChannelPlan(channelPlan);
            //logger.info("here-3-6");
        }
        //logger.info("here-4");
        return new Plan(version, channelMapping);
    }
}
