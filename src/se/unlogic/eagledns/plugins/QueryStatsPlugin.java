package se.unlogic.eagledns.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Timer;

import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.readwrite.ReadWriteUtils;
import se.unlogic.standardutils.time.MillisecondTimeUnits;
import se.unlogic.standardutils.timer.RunnableTimerTask;


public class QueryStatsPlugin extends BasePlugin implements Runnable{

	protected static final String DNS_REQUESTS_HANDLED_PREFIX = "DNS-REQUESTS-HANDLED: ";
	protected static final String DNS_REPLIES_SPOOFED_PREFIX = "DNS-REPLIES-SPOOFED: ";
	protected static final String DNS_REQUESTS_TIMEOUT_PREFIX = "DNS-REQUESTS-TIMEOUT: ";
	
	protected long savingInterval = 1 * MillisecondTimeUnits.MINUTE;
	
	protected String filePath;
	protected File file;
	
	protected long requestsHandledOffset;
	protected long requestsSpoofedOffset;
	protected long requestsTimedoutOffset;
	
	protected Timer timer;
	
	@Override
	public void init(String name) throws Exception {

		super.init(name);
		
		if(filePath == null){
			
			throw new RuntimeException("No file set!");
			
		}
		
		file = new File(filePath);
		
		if(file.exists()){
			
			log.info("Plugin " + name + " reading previously saved statistics from file " + file.getAbsolutePath());
			
			FileReader fileReader = null;
			BufferedReader bufferedReader = null;
			
			try{
				fileReader = new FileReader(file);
				bufferedReader = new BufferedReader(fileReader);
				
				while(bufferedReader.ready()){
					
					String line = bufferedReader.readLine();
					
					if(line.startsWith(DNS_REQUESTS_HANDLED_PREFIX) && line.length() > DNS_REQUESTS_HANDLED_PREFIX.length()){
						
						Long value = NumberUtils.toLong(line.substring(DNS_REQUESTS_HANDLED_PREFIX.length()));
						
						if(value != null){
														
							this.requestsHandledOffset = value;
						}
						
					}else if(line.startsWith(DNS_REPLIES_SPOOFED_PREFIX) && line.length() > DNS_REPLIES_SPOOFED_PREFIX.length()){
						
						Long value = NumberUtils.toLong(line.substring(DNS_REPLIES_SPOOFED_PREFIX.length()));
						
						if(value != null){
							
							this.requestsSpoofedOffset = value;
						}
						
					}else if(line.startsWith(DNS_REQUESTS_TIMEOUT_PREFIX) && line.length() > DNS_REQUESTS_TIMEOUT_PREFIX.length()){
						
						Long value = NumberUtils.toLong(line.substring(DNS_REQUESTS_TIMEOUT_PREFIX.length()));
						
						if(value != null){
							
							this.requestsTimedoutOffset = value;
						}
					}
				}
				
			}finally{
				ReadWriteUtils.closeReader(bufferedReader);
				ReadWriteUtils.closeReader(fileReader);
			}
			
		}else{
			
			log.info("Plugin " + name + " found no previously saved statistics,, creating new file on first save");
		}
		
		timer = new Timer(true);
		
		timer.schedule(new RunnableTimerTask(this), savingInterval, savingInterval);
	}
	
	@Override
	public void shutdown() throws Exception {

		timer.cancel();
		
		super.shutdown();
	}

	
	public void setSavingInterval(String savingInterval) {
	
		Long interval = NumberUtils.toLong(savingInterval);
		
		if(interval == null || interval > 1){
			
			log.error("Invalid saving interval value " + savingInterval + " specified, falling back to default value of " + this.savingInterval + "ms");
		
		}else{
		
			this.savingInterval = interval;
		}
	}

	public void run() {

		FileWriter fileWriter = null;
		
		try{
			fileWriter = new FileWriter(file);
			
			//TODO resume here
			
		}catch(Throwable t){
			
		}finally{
			
			ReadWriteUtils.closeWriter(fileWriter);
		}
	}
}