/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
package org.finra.herd.metastore.managed;

import com.google.common.collect.Lists;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.hive.FormatChange;
import org.finra.herd.metastore.managed.hive.HiveTableSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.COMMA;

@Component
@Slf4j
public class NotificationSender {

	@Value( "${MAILING_LIST}" )
	private String mailingList;

	@Value( "${ENV}" )
	String env;

	@Value( "${FORMAT_CHANGE_MAILING_LIST}" )
	private String formatChangeMailingList;

	@Value( "${email_host}" )
	private String emailHost;

	PebbleEngine engine = new PebbleEngine.Builder().autoEscaping( false ).strictVariables( true ).build();

	public void sendFailureEmail( JobDefinition od, int numRetry, String errorLog, String clusterID ) {

		String action = getAction( od );

		String subject = String.format( "%s Failed After %d Retries", action, numRetry );

		sendNotificationEmail( getMessageBody( od, errorLog, clusterID ), subject, od );
	}

	protected String getAction( JobDefinition od ) {
		String action = "Add Partition";
		if ( od.getWfType() == ObjectProcessor.WF_TYPE_MANAGED_STATS ) {
			action = "Gather Stats";
		} else if ( od.getWfType() == ObjectProcessor.WF_TYPE_DROP_TABLE ) {
			action = "Drop Table";
		}
		return action;
	}

	private String getMessageBody( JobDefinition od, String errorLog, String clusterID ) {
		if ( od.getPartitionValue().contains( COMMA ) ) {
			return String.format( "Cluster ID = %s, Execution ID = %s \n Partition Values: %s \n\n %s", clusterID, od.getExecutionID(), od.getPartitionValue(), errorLog );
		}

		return String.format( "Cluster ID = %s, Execution ID = %s \n %s", clusterID, od.getExecutionID(), errorLog );
	}

	public void sendNotificationEmail( String msgBody, String subject, JobDefinition od ) {
		// Update subject with JD details
		String partitionValue = od.getPartitionValue();

		if ( od.getPartitionValue().contains( COMMA ) ) {
			partitionValue = Lists.newArrayList( partitionValue.split( COMMA ) ).stream().limit( 5 ).collect( Collectors.joining( COMMA ) ).concat( "..." );
		}
		String fullSubject = String.format( "%s for %s-%s-%s-%s-%s", subject,
				od.getObjectDefinition().getNameSpace(), od.getObjectDefinition().getObjectName(),
				od.getObjectDefinition().getUsageCode(), od.getObjectDefinition().getFileType(), partitionValue );

		sendEmail( msgBody, fullSubject );
	}

	public void sendEmail( String msgBody, String subject ) {
		Properties prop = new Properties( System.getProperties() );
		prop.put( "mail.smtp.host", emailHost );

		Session session = Session.getDefaultInstance( prop, null );

		try {

			Message msg = new MimeMessage( session );
			msg.setFrom( new InternetAddress( "donotreply@finra.org", "METASTOR" ) );

			msg.addRecipient( Message.RecipientType.TO, new InternetAddress( mailingList ) );

			msg.setSubject( String.format( "METASTOR-%s %s", env, subject ) );

			msg.setDataHandler( new DataHandler( new ByteArrayDataSource( msgBody, "text/plain" ) ) );
			javax.mail.Transport.send( msg );
		} catch ( Exception ex ) {
			log.error( ex.getMessage() );
		}
	}

	public void sendFormatChangeEmail( FormatChange change, int version, JobDefinition job,
									   HiveTableSchema existing, HiveTableSchema newColumns ) {
		try {
			String msg = getFormatChangeMsg( change, version, job, existing, newColumns );
			sendNotificationEmail( msg, "format changed", job );
		} catch ( Exception ex ) {
			log.error( "Failed to send format notification, " + ex.getMessage() );
		}
	}

	protected String getFormatChangeMsg( FormatChange change, int version, JobDefinition job,
							   HiveTableSchema existing, HiveTableSchema newColumns )
			throws PebbleException, IOException {
		PebbleTemplate template = engine.getTemplate( "templates/formatChangeNotificationTemplate.txt" );

		Map<String, Object> context = new HashMap<>();

		context.put( "changes", change );
		context.put( "nameSpace", job.getObjectDefinition().getNameSpace() );
		context.put( "tableName", job.getTableName() );
		context.put( "version", version );

		context.put( "existing", existing );
		context.put( "new", newColumns );

		StringWriter writer = new StringWriter();
		template.evaluate( writer, context );
		return writer.toString();
	}

}
