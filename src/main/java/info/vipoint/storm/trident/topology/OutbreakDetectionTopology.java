package info.vipoint.storm.trident.topology;

import info.vipoint.storm.trident.operator.*;
import info.vipoint.storm.trident.spout.DiagnosisEventSpout;
import info.vipoint.storm.trident.state.OutbreakTrendFactory;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.tuple.Fields;
import storm.trident.Stream;
import storm.trident.TridentTopology;
import storm.trident.operation.builtin.Count;

public class OutbreakDetectionTopology {

    public static StormTopology buildTopology() {
        TridentTopology topology = new TridentTopology();
        DiagnosisEventSpout spout = new DiagnosisEventSpout();
        Stream inputStream = topology.newStream("event", spout);

        inputStream.each(new Fields("event"), new DiseaseFilter())
                .each(new Fields("event"), new CityAssignment(), new Fields("city"))
                .each(new Fields("event", "city"), new HourAssignment(), new Fields("hour", "cityDiseaseHour"))
                .groupBy(new Fields("cityDiseaseHour"))
                .persistentAggregate(new OutbreakTrendFactory(), new Count(), new Fields("count")).newValuesStream()
                .each(new Fields("cityDiseaseHour", "count"), new OutbreakDetector(), new Fields("alert"))
                .each(new Fields("alert"), new DispatchAlert(), new Fields());
        return topology.build();
    }

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        
        if(args!=null && args.length > 0) {
			config.setNumWorkers(3);
			
			//submit topology to a remote cluster
			StormSubmitter.submitTopology(args[0], config, buildTopology());
		} else {
			//storm's local mode
			LocalCluster cluster = new LocalCluster();
			
			//submit topology to the local cluster
			cluster.submitTopology("sns", config, buildTopology());
			
			backtype.storm.utils.Utils.sleep(200000);
			cluster.killTopology("sns");
			cluster.shutdown();
		}
      }
}
