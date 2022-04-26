package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
//@Rollback(false)
public class QuerydslBasicTest {

	@Autowired EntityManager em;

	// EntityManager that spring injects, is designed to be no concurrency problem.
	// so it can be used in multi-threaded form.
	// even

	JPAQueryFactory queryFactory;

	@BeforeEach
	public void becore() {
		queryFactory = new JPAQueryFactory(em);

		Team teamA = new Team("teamA");
		Team teamB = new Team("teamB");
		em.persist(teamA);
		em.persist(teamB);

		Member member1 = new Member("member1", 10, teamA);
		Member member2 = new Member("member2", 20, teamA);
		Member member3 = new Member("member3", 30, teamB);
		Member member4 = new Member("member4", 40, teamB);

		em.persist(member1);
		em.persist(member2);
		em.persist(member3);
		em.persist(member4);
	}

	@Test
	public void startJPQL() {
		// find member1
		String query = "" +
				"select m from Member m" +
				" where m.username = :username";

		Member findMember = em.createQuery(query, Member.class)
				.setParameter("username", "member1")
				.getSingleResult();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void startQuerydsl() {
		//JPAQueryFactory queryFactory = new JPAQueryFactory(em);

		// QMember member = new QMember("m");
		// QMember member = QMember.member;

		Member findMember = queryFactory
				.select(member)
				.from(member)
				.where(member.username.eq("member1")) // make preparedStatement and bind parameter
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void search() {
		Member findMember = queryFactory
				.selectFrom(member)
				.where(member.username.eq("member1")
						.and(member.age.eq(10)))
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void searchAndParam() {
		Member findMember = queryFactory
				.selectFrom(member)
				.where(
						member.username.eq("member1"),
						member.age.between(10,30))
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void resultFetch() {
		List<Member> fetch = queryFactory
				.selectFrom(member)
				.fetch();


		// com.querydsl.core.NonUniqueResultException
		/*Member fetchOne = queryFactory
				.selectFrom(member)
				.fetchOne();*/

		Member fetchFirst = queryFactory
				.selectFrom(member)
				.fetchFirst();// .limit(1).fetchOne()

		// deprecated
		/*QueryResults<Member> results = queryFactory
				.selectFrom(member)
				.fetchResults();
		results.getTotal();
		List<Member> content = results.getResults();

		long total = queryFactory
				.selectFrom(member)
				.fetchCount();*/
	}
}
