package org.speedd.dm;

import java.util.Map;

import org.speedd.data.Event;

public class FreewayCell {
	// saved object parameters
	protected final FreewayCell_IdTable id_table;
	protected final Ctm ctm_data;
	protected final int k;
	protected final double q_length;
	
	// mainline state estimator and sysId
	protected final FreewayStateEstimator mainline_stateEstimator;
	protected final FreewayStateEstimator onramp_stateEstimator;
	protected final FreewaySysId mainline_sysId;
	protected double merge_density = 0.; // assume empty freeway initially
	
	// constants
	private final double MAX_MAINLINE_DENSITY = 250.;
	
	/**
	 * Default constructor.
	 * 
	 * @param id_table
	 * @param ctm_data
	 * @param dt
	 */
	public FreewayCell(FreewayCell_IdTable id_table, Ctm ctm_data, double q_length, double dt) {
		// save parameters
		this.id_table = id_table;
		this.ctm_data = ctm_data;
		this.q_length = q_length;
		this.k = GrenobleTopology.get_index( id_table.actu_id );
		
		// create observer for mainline
		this.mainline_stateEstimator = new FreewayStateEstimator(this.ctm_data.l, 250., dt);
		// create observer for onramp
		if (q_length > 0) {
			this.onramp_stateEstimator = new FreewayStateEstimator(q_length, 125., dt);
		} else {
			this.onramp_stateEstimator = null;
		}
		// create sysId for mainline
		this.mainline_sysId = new FreewaySysId(0.7 * this.ctm_data.v, this.ctm_data.rhoc, this.ctm_data.rhom, this.ctm_data.l);
	}
	
	/**
	 * Estimate the traffic density in the merge area.
	 * @param merge_density_us_estimate
	 * @return
	 */
	private double estimate_merge_density(double merge_density_us_estimate) {
		// get variables
		double mainline_density = this.mainline_stateEstimator.getDensity();
		if (this.onramp_stateEstimator == null) {
			return mainline_density; // no onramp present...
		}
		double mainline_flow = this.mainline_stateEstimator.getFlow();
		double inflow_estimate = this.onramp_stateEstimator.getFlow();
		// estimation of downstream density in the merge-area
		double merge_density = mainline_density * ((mainline_flow + inflow_estimate) / (mainline_flow + (1e-1))); // Better safe than sorry, noise is large for low flows.
		
		// return this.mainline.getMergeDensity();
		return Math.max( Math.min( merge_density, this.MAX_MAINLINE_DENSITY), merge_density_us_estimate );
	}
	
	/**
	 * Getter for merge density.
	 * @return
	 */
	public double get_merge_density() {
		return this.merge_density;
	}
	
	/**
	 * Process a measurement event w.r.t. state estimation and system identification
	 * 
	 * @param event		a CEPevent
	 */
	public void processMeasurement(Event event) {

		String eventName = event.getEventName();
        if (eventName.equals("AverageDensityAndSpeedPerLocationOverInterval") || eventName.equals("AverageOnRampValuesOverInterval"))
        {
        	// read attributes
    		// long timestamp = event.getTimestamp();
        	Map<String, Object> attributes = event.getAttributes();
        	
        	// check that all expected fields are valid
        	if ((attributes.get("average_flow") != null) && (attributes.get("average_occupancy") != null) && (attributes.get("average_speed") != null) 
        			&& (attributes.get("standard_dev_flow") != null) && (attributes.get("standard_dev_density") != null) && (attributes.get("sensorId") != null) ) {
        		
        		// read attributes
        		double mean_flow 	= TrafficDecisionMakerBolt.CARS_2_FLOW * ((double) attributes.get("average_flow"));
        		double mean_density = TrafficDecisionMakerBolt.OCCU_2_DENS * ((double) attributes.get("average_occupancy"));
        		double velocity 	= 										  (double) attributes.get("average_speed");
        		double stdv_flow 	= TrafficDecisionMakerBolt.CARS_2_FLOW * ((double) attributes.get("standard_dev_flow"));
        		double stdv_density = TrafficDecisionMakerBolt.OCCU_2_DENS * ((double) attributes.get("standard_dev_density"));
        		
        		// sensorId
            	int sensorId = Integer.parseInt((String) attributes.get("sensorId"));
            	
        		if ( Double.isNaN(mean_flow + mean_density + velocity + stdv_flow + stdv_density) ) {
        			return; // ignore measurement in case of NaN to ensure validity of internal state
        		}
            	
            	// mainline state estimation + system identification.
            	if (sensorId == this.id_table.sens_in) {
            		this.mainline_stateEstimator.processInMeasurement(mean_flow, stdv_flow, mean_density, stdv_density, velocity);
            	} else if (sensorId == this.id_table.sens_ou) {
            		this.mainline_stateEstimator.processOutMeasurement(mean_flow, stdv_flow, mean_density, stdv_density, velocity);
            	} else if (sensorId == this.id_table.sens_me) {
            		this.merge_density = this.estimate_merge_density(mean_density);
            		double flow = this.mainline_stateEstimator.getFlow();
            		this.mainline_sysId.addDatum(flow, this.merge_density); // NOTE: sysId is ONLY done for sections with onramps, using the density in the merge area
            	}
            	
            	// onramp state estimation
            	if (sensorId == this.id_table.sens_qu) {
            		stdv_density = 1000.; // NOTE: should be ignored for this event anyway...
            		this.onramp_stateEstimator.processInMeasurement(mean_flow, stdv_flow, mean_density, stdv_density, velocity);
            	} else if (sensorId == this.id_table.sens_on) {
            		stdv_density = 1000.; // density cannot be inferred from occupancy on onramps
            		mean_flow = Math.max(mean_flow, TrafficDecisionMakerBolt.RMIN); // adjust to min. metering rate
            		this.onramp_stateEstimator.processOutMeasurement(mean_flow, stdv_flow, mean_density, stdv_density, velocity);
            	}

            	
        	} // end if <correct event>
        } // end if <attributes are valid>
	}

}
