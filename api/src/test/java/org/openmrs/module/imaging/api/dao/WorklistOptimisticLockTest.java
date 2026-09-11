package org.openmrs.module.imaging.api.dao;

import org.hibernate.StaleStateException;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.imaging.api.worklist.RequestProcedure;
import org.openmrs.module.imaging.api.worklist.RequestProcedureStep;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.BeanUtils;

import static org.junit.Assert.*;

/** Uses detached snapshots to reproduce stale writes without thread scheduling assumptions. */
public class WorklistOptimisticLockTest extends BaseModuleContextSensitiveTest {

	private DbSession session;
	private RequestProcedureDao requests;
	private RequestProcedureStepDao steps;

	@Before
	public void setUp() throws Exception {
		executeDataSet("testRequestProcedureStepDataset.xml");
		session = applicationContext.getBean("dbSessionFactory", DbSessionFactory.class).getCurrentSession();
		requests = applicationContext.getBean("imaging.RequestProcedureDao", RequestProcedureDao.class);
		steps = applicationContext.getBean("imaging.RequestProcedureStepDao", RequestProcedureStepDao.class);
	}

	@Test
	public void aStaleRequestCannotUndoACompletedRequest() {
		RequestProcedure current = requests.get(1);
		assertEquals(Integer.valueOf(0), current.getVersion());
		RequestProcedure stale = new RequestProcedure();
		BeanUtils.copyProperties(current, stale);
		current.setStatus("completed");
		requests.update(current);
		session.flush();
		assertEquals(Integer.valueOf(1), current.getVersion());
		stale.setStatus("scheduled");
		RuntimeException failure = assertThrows(RuntimeException.class, () -> session.merge(stale));
		assertOptimisticConflict(failure);
		assertEquals("completed", current.getStatus());
	}

	@Test
	public void aStaleCallbackCannotReplaceAClinicalRejection() {
		RequestProcedureStep current = steps.get(1);
		assertEquals(Integer.valueOf(0), current.getVersion());
		RequestProcedureStep stale = new RequestProcedureStep();
		BeanUtils.copyProperties(current, stale);
		steps.updatePerformedProcedureStepStatus(current, "rejected");
		session.flush();
		assertEquals(Integer.valueOf(1), current.getVersion());
		stale.setPerformedProcedureStepStatus("completed");
		RuntimeException failure = assertThrows(RuntimeException.class, () -> session.merge(stale));
		assertOptimisticConflict(failure);
		assertEquals("rejected", current.getPerformedProcedureStepStatus());
	}

	private void assertOptimisticConflict(Throwable failure) {
		Throwable cause = failure;
		while (cause != null) {
			if (cause instanceof StaleStateException || cause instanceof javax.persistence.OptimisticLockException) {
				return;
			}
			cause = cause.getCause();
		}
		fail("Expected an optimistic locking conflict, received " + failure.getClass().getName());
	}
}
