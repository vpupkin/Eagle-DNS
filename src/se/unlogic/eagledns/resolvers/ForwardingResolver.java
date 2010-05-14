package se.unlogic.eagledns.resolvers;

import java.io.IOException;
import java.util.LinkedList;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;

import se.unlogic.eagledns.EagleDNS;
import se.unlogic.eagledns.Request;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.time.MillisecondTimeUnits;
import se.unlogic.standardutils.time.TimeUtils;

public class ForwardingResolver extends BaseResolver implements Runnable {

	protected String server;
	protected int port = 53;
	protected boolean tcp;

	protected Integer timeout;

	protected Integer maxerrors;
	protected Integer errorWindowsSize;

	protected String validationQuery = "google.com";
	protected int validationInterval = 5;

	protected SimpleResolver resolver;

	protected boolean online = true;

	private LinkedList<Long> errors = null;

	protected Lookup lookup;

	@Override
	public void init(String name) throws Exception {

		super.init(name);

		if (server == null) {

			throw new RuntimeException("No server set!");
		}

		this.resolver = new SimpleResolver(server);
		this.resolver.setPort(port);

		if (timeout != null) {

			this.resolver.setTimeout(timeout);
		}

		if (this.maxerrors != null && this.errorWindowsSize != null) {

			log.info("Resolver " + name + " has maxerrors and errorWindowsSize set, enabling failover detection");

			this.errors = new LinkedList<Long>();

			lookup = new Lookup(this.validationQuery);
			lookup.setResolver(resolver);
		}
	}

	public Message generateReply(Request request) {

		if (this.online) {

			try {
				log.debug("Resolver " + name + " forwarding query " + EagleDNS.toString(request.getQuery().getQuestion()) + " to server " + server + ":" + port);

				Message response = resolver.send(request.getQuery());

				log.debug("Resolver " + name + " got response " + Rcode.string(response.getHeader().getRcode()) + " with " + response.getSectionArray(Section.ANSWER).length + " answer, " + response.getSectionArray(Section.AUTHORITY).length + " authoritative and " + response.getSectionArray(Section.ADDITIONAL).length + " additional records");

				Integer rcode = response.getHeader().getRcode();

				if (rcode == null || rcode == Rcode.NXDOMAIN || rcode == Rcode.SERVFAIL ||(rcode == Rcode.NOERROR && response.getSectionArray(Section.ANSWER).length == 0 && response.getSectionArray(Section.AUTHORITY).length == 0)) {

					return null;
				}

				return response;

			} catch (IOException e) {

				log.warn("Error " + e + " in resolver " + name + " while fowarding query " + EagleDNS.toString(request.getQuery().getQuestion()));

				processError();

			} catch (RuntimeException e) {

				log.warn("Error " + e + " in resolver " + name + " while fowarding query " + EagleDNS.toString(request.getQuery().getQuestion()));

				processError();
			}
		} else {

			log.debug("Resolver " + this.name + " is offline skipping query " + EagleDNS.toString(request.getQuery().getQuestion()));
		}

		return null;
	}

	public synchronized void processError() {

		System.out.println("Errors in list: " + errors);

		long currentTime = System.currentTimeMillis();

		errors.add(currentTime);

		if (errors.size() > maxerrors) {

			errors.removeFirst();

			if (online && errors.getFirst() > (currentTime - (MillisecondTimeUnits.SECOND * errorWindowsSize))) {

				log.warn("Marking resolver " + name + " as offline after receiving " + maxerrors + " errors in " + TimeUtils.millisecondsToString((currentTime - errors.getFirst())));

				this.online = false;

				Thread thread = new Thread(this);

				thread.setDaemon(true);

				thread.start();
			}
		}
	}

	public void setServer(String server) {

		this.server = server;
	}

	/**
	 * The connection time in seconds
	 * 
	 * @param stringTimeout
	 */
	public void setTimeout(String stringTimeout) {

		if (timeout != null) {

			Integer timeout = NumberUtils.toInt(stringTimeout);

			if (timeout == null || timeout < 1) {

				log.warn("Invalid timeout " + stringTimeout + " specified");

			} else {

				this.timeout = timeout;
			}

		} else {

			this.timeout = null;
		}
	}

	public void setMaxerrors(String maxerrorsString) {

		if (maxerrorsString != null) {

			Integer maxerrors = NumberUtils.toInt(maxerrorsString);

			if (maxerrors == null || maxerrors < 1) {

				log.warn("Invalid max error value " + maxerrorsString + " specified");

			} else {

				this.maxerrors = maxerrors;
			}

		} else {

			this.maxerrors = null;
		}
	}

	public void setErrorWindowsSize(String errorWindowsSizeString) {

		if (errorWindowsSizeString != null) {

			Integer errorWindowsSize = NumberUtils.toInt(errorWindowsSizeString);

			if (errorWindowsSize == null || errorWindowsSize < 1) {

				log.warn("Invalid error window size " + errorWindowsSizeString + " specified");

			} else {

				this.errorWindowsSize = errorWindowsSize;
			}

		} else {

			this.errorWindowsSize = null;
		}
	}

	public void setPort(String portString) {

		Integer port = NumberUtils.toInt(portString);

		if (port != null && port >= 1 && port <= 65536) {

			this.port = port;

		} else {

			log.warn("Invalid port " + portString + " specified! (sticking to default value " + this.port + ")");
		}
	}

	public void setTcp(String tcp) {

		this.tcp = Boolean.parseBoolean(tcp);
	}

	public void setValidationQuery(String validationQuery) {

		this.validationQuery = validationQuery;
	}


	public void setValidationInterval(String validationIntervalString) {

		Integer validationInterval = NumberUtils.toInt(validationIntervalString);

		if (validationInterval != null && validationInterval > 0) {

			this.validationInterval = validationInterval;

		} else {

			log.warn("Invalid validation interval " + validationIntervalString + " specified!");
		}
	}

	public void run() {

		log.info("Status monitoring thread for resolver " + name + " started");

		while(true){

			try {
				lookup.run();

				if(lookup.getResult() == Lookup.SUCCESSFUL){

					log.info("Marking resolver " + this.name + " as online after getting succesful response from query for " + this.validationQuery);
					this.online = true;
					return;

				}else{

					log.debug("Resolver " + this.name + " is still down, got response " + Rcode.string(lookup.getResult()) + " from upstream server for query " + validationQuery);
				}

			} catch (RuntimeException e) {

				log.debug("Resolver " + this.name + " is still down, got error " + e + " when trying to resolve " + this.validationQuery);
			}

			try {
				Thread.sleep(this.validationInterval * MillisecondTimeUnits.SECOND);
			} catch (InterruptedException e) {}
		}
	}
}
