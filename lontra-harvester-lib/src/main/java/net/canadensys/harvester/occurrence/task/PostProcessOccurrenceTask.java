package net.canadensys.harvester.occurrence.task;

import java.math.BigInteger;
import java.util.Map;

import net.canadensys.dataportal.occurrence.model.DwcaResourceModel;
import net.canadensys.dataportal.occurrence.model.OccurrenceFieldConstants;
import net.canadensys.harvester.ItemTaskIF;
import net.canadensys.harvester.exception.TaskExecutionException;
import net.canadensys.harvester.occurrence.SharedParameterEnum;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complementary task to set occurrence's resourcename and publishername fields, as well as update occurrence record counts to dwca_resource and publisher tables.
 *
 * @author Pedro Guimarães
 *
 */
public class PostProcessOccurrenceTask implements ItemTaskIF {

	// get log4j handler
	private static final Logger LOGGER = Logger.getLogger(PostProcessOccurrenceTask.class);

	@Autowired
	@Qualifier(value = "bufferSessionFactory")
	private SessionFactory sessionFactory;

	@Transactional("bufferTransactionManager")
	@Override
	public void execute(Map<SharedParameterEnum, Object> sharedParameters)
			throws TaskExecutionException {
		String publisherName = (String) sharedParameters.get(SharedParameterEnum.PUBLISHER_NAME);
		Integer resourceId = (Integer) sharedParameters.get(SharedParameterEnum.RESOURCE_ID);
		DwcaResourceModel resourceModel = (DwcaResourceModel) sharedParameters.get(SharedParameterEnum.RESOURCE_MODEL);

		if (resourceModel == null || resourceId == null) {
			LOGGER.fatal("Misconfigured task : resourceModel and resourceId are required.");
			throw new TaskExecutionException("Misconfigured PostProcessOccurrenceTask");
		}

		Session session = sessionFactory.getCurrentSession();

		try {
			// Update resource name for this dataset's occurrence records:
			SQLQuery query = session.createSQLQuery("update buffer.occurrence set resourcename = ? where " + OccurrenceFieldConstants.RESOURCE_ID
					+ " = ?;");

			query.setString(0, resourceModel.getName());
			query.setInteger(1, resourceId);
			query.executeUpdate();

			// TODO update after record_count is move to resource_metadata table
			// Update dwca_resource record_count for this dataset:

			query = session
					.createSQLQuery("update dwca_resource dr set record_count = (select count(occ.auto_id) from buffer.occurrence occ where occ.resource_id = dr.id) where dr.id = ?;");
			query.setInteger(0, resourceId);
			query.executeUpdate();

			// In case the dataset is related to a publisher in the GUI:
			if (publisherName != null   || StringUtils.isNotBlank(publisherName)) {
				// Update publisher name for this dataset's occurrence records:
				query = session.createSQLQuery("update buffer.occurrence set publishername = ? where " + OccurrenceFieldConstants.RESOURCE_ID
						+ " = ?;");
				query.setString(0, publisherName);
				query.setInteger(1, resourceId);
				query.executeUpdate();

				/* Updating publisher record counts */

				// Get dataset's publisher pk:
				query = session
						.createSQLQuery("select pu.auto_id from publisher pu inner join dwca_resource dr on (dr.publisher_fkey=pu.auto_id) where dr.id = ? ;");
				query.setInteger(0, resourceId);
				Integer publisherFkey = (Integer)query.uniqueResult();
				// Get record count for all the publishers' dwca_resources:
				if (publisherFkey != null) {
					query = session.createSQLQuery("select distinct sum(dr.record_count) from dwca_resource dr where dr.publisher_fkey = ?;");
					query.setInteger(0, publisherFkey);
					BigInteger sum = (BigInteger) query.uniqueResult();
					// Update publisher with total record count:
					query = session.createSQLQuery("update publisher pu set record_count = ? where auto_id = ? ;");
					query.setBigInteger(0, sum);
					query.setInteger(1, publisherFkey);
					query.executeUpdate();
				}
			}

			LOGGER.info("PostProcessOccurrence finished successfully.");
		}
		catch (HibernateException hEx) {
			LOGGER.fatal("Can't execute PostProcessOccurrenceTask on the database.", hEx);
			throw new TaskExecutionException("Can't execute PostProcessOccurrenceTask on the database.");
		}
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public String getTitle() {
		return "Post processing occurrence task.";
	}
}
