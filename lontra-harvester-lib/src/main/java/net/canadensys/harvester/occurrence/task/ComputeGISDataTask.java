package net.canadensys.harvester.occurrence.task;

import java.util.Map;

import net.canadensys.dataportal.occurrence.model.DwcaResourceModel;
import net.canadensys.harvester.ItemTaskIF;
import net.canadensys.harvester.exception.TaskExecutionException;
import net.canadensys.harvester.occurrence.SharedParameterEnum;

import org.apache.log4j.Logger;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task to compute all GIS related data in our PostGIS enabled table.
 * To ensure maximum performance we run this once at the end of the import
 *
 * @author canadensys
 *
 */
public class ComputeGISDataTask implements ItemTaskIF {

	// get log4j handler
	private static final Logger LOGGER = Logger.getLogger(ComputeGISDataTask.class);

	@Autowired
	@Qualifier(value = "bufferSessionFactory")
	private SessionFactory sessionFactory;

	/**
	 * @param @param sharedParameters SharedParameterEnum.SOURCE_FILE_ID required
	 */
	@Transactional("bufferTransactionManager")
	@Override
	public void execute(Map<SharedParameterEnum, Object> sharedParameters) {
		DwcaResourceModel resourceModel = (DwcaResourceModel) sharedParameters.get(SharedParameterEnum.RESOURCE_MODEL);

		if (resourceModel == null) {
			LOGGER.fatal("Misconfigured task : needs  resourceModel");
			throw new TaskExecutionException("Misconfigured task");
		}

		Session session = sessionFactory.getCurrentSession();
		String sourceFileId = resourceModel.getSourcefileid();
		// update the_geom
		SQLQuery query = session
				.createSQLQuery("UPDATE buffer.occurrence SET the_geom = st_geometryfromtext('POINT('||decimallongitude||' '|| decimallatitude ||')',4326) "
						+ "WHERE sourcefileid=? AND decimallatitude IS NOT NULL AND decimallongitude IS NOT NULL");
		query.setString(0, sourceFileId);
		query.executeUpdate();

		// update the_geom_webmercator
		query = session
				.createSQLQuery("UPDATE buffer.occurrence SET the_geom_webmercator = st_transform_null(the_geom,3857) WHERE sourcefileid=? AND the_geom IS NOT NULL");
		query.setString(0, sourceFileId);
		query.executeUpdate();

		// update the_shifted_geom
		query = session
				.createSQLQuery("UPDATE buffer.occurrence SET the_shifted_geom = ST_Shift_Longitude(the_geom) WHERE sourcefileid=? AND the_geom IS NOT NULL");
		query.setString(0, sourceFileId);
		query.executeUpdate();
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public String getTitle() {
		return "Computing GIS info";
	}
}
