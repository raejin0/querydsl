package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.TypedQuery;

import java.nio.channels.NotYetBoundException;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

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

	// beginning - jpql vs querydsl
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

	// search conditions query
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

	// fetch type for results
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

	/**
	 * sorting order for Member
	 * 1. age desc
	 * 2. name asc
	 * if there's no name, print at last ( null last )
	 */
	@Test
	public void sort() {
		em.persist(new Member(null, 100));
		em.persist(new Member("member5", 100));
		em.persist(new Member("member6", 100));

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.eq(100))
				.orderBy(member.age.desc(), member.username.asc().nullsLast())
				.fetch();

		Member member5 = result.get(0);
		Member member6 = result.get(1);
		Member memberNull = result.get(2);

		assertThat(member5.getUsername()).isEqualTo("member5");
		assertThat(member6.getUsername()).isEqualTo("member6");
		assertThat(memberNull.getUsername()).isNull(); // isEqualTo(null);
	}

	// paging
	@Test
	public void paging1() {
		List<Member> result = queryFactory
				.selectFrom(member)
				.orderBy(member.username.desc())
				.offset(1)
				.limit(2)
				.fetch();

		assertThat(result.size()).isEqualTo(2);
	}

	// grouping
	@Test
	public void aggregation() { // 집합, 통계, 종합
		List<Tuple> result = queryFactory
				.select(
						member.count(),
						member.age.sum(),
						member.age.avg(),
						member.age.max(),
						member.age.min()
						)
				.from(member)
				.fetch();

		Tuple tuple = result.get(0);
		assertThat(tuple.get(member.count())).isEqualTo(4);
		assertThat(tuple.get(member.age.sum())).isEqualTo(100);
		assertThat(tuple.get(member.age.avg())).isEqualTo(25);
		assertThat(tuple.get(member.age.max())).isEqualTo(40);
		assertThat(tuple.get(member.age.min())).isEqualTo(10);
	}

	/**
	 * get team name and average age of each team
	 */
	@Test
	public void group() throws Exception {
		List<Tuple> result = queryFactory
				.select(team.name, member.age.avg())
				.from(member)
				.join(member.team, team)
				.groupBy(team.name)
				.fetch();

		Tuple teamA = result.get(0);
		Tuple teamB = result.get(1);

		assertThat(teamA.get(team.name)).isEqualTo("teamA");
		assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20 ) / 2

		assertThat(teamB.get(team.name)).isEqualTo("teamB");
		assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40 ) / 2
	}

	// join - basic
	/**
	 * All members in teamA
	 */
	@Test
	public void join(){
		List<Member> result = queryFactory
				.selectFrom(member)
				.join(member.team, team)
				.where(team.name.eq("teamA"))
				.fetch();

		assertThat(result)
				.extracting("username")
				.containsExactly("member1", "member2");
	}

	/** theta join
	 * find memberName == teamName
	 */
	@Test
	public void theta_join() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Member> result = queryFactory
				.select(member)
				.from(member, team)
				.where(member.username.eq(team.name))
				.fetch();

		assertThat(result)
				.extracting("username")
				.containsExactly("teamA", "teamB");
	}

	// join - on
	/** join member and team, all member and teamA
	 *  JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
	 * */
	@Test
	public void join_on_filtering(){
		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.leftJoin(member.team, team)
				.on(team.name.eq("teamA"))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	/** outer join entities with no relations
	 * memberName == teamName
	 */
	@Test
	public void join_in_no_realation() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		// theta join
		/*List<Member> result = queryFactory
				.select(member)
				.from(member, team)
				.where(member.username.eq(team.name))
				.fetch();

		assertThat(result)
				.extracting("username")
				.containsExactly("teamA", "teamB");*/

		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.leftJoin(team)
				.on(member.username.eq(team.name))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	// join - fetch
	@PersistenceUnit EntityManagerFactory emf;

	@Test
	public void fetchJoinNo(){
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.where(member.username.eq("member1"))
				.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("fetch join not applied").isFalse();
	}

	@Test
	public void fetchJoinUse(){
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.join(member.team, team).fetchJoin()
				.where(member.username.eq("member1"))
				.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("fetch join not applied").isTrue();
	}

	// subquery

	/** find oldest member */
	@Test
	public void subQuery(){
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.eq(
						select(memberSub.age.max())
								.from(memberSub)
				))
				.fetch();

		assertThat(result)
				.extracting("age")
				.containsExactly(40);
	}

	/** member whose age is greater than or equal to average */
	@Test
	public void subQueryGoe(){
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.goe(
						select(memberSub.age.avg())
								.from(memberSub)
				))
				.fetch();

		assertThat(result)
				.extracting("age")
				.containsExactly(30, 40);
	}

	@Test
	public void subQueryIn(){
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.in(
						select(memberSub.age)
								.from(memberSub)
								.where(memberSub.age.gt(10))
				))
				.fetch();

		assertThat(result)
				.extracting("age")
				.containsExactly(20, 30, 40);
	}

	@Test
	public void selectSubQuery(){

		QMember memberSub = new QMember("memberSub");

		List<Tuple> result = queryFactory
				.select(member.username,
						select(memberSub.age.avg())
								.from(memberSub))
				.from(member)
				.fetch();
		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	// case clause
	@Test
	public void basicCase(){
		List<String> result = queryFactory
				.select(member.age
						.when(10).then("열살")
						.when(20).then("스무살")
						.otherwise("기타"))
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void complexCase(){
		List<String> result = queryFactory
				.select(new CaseBuilder()
						.when(member.age.between(0, 20)).then("0~20살")
						.when(member.age.between(21, 30)).then("21~30살")
						.otherwise("기타")
				)
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	// constant addition
	@Test
	public void constant(){
		List<Tuple> result = queryFactory
				.select(member.username, Expressions.constant("A"))
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	// concat
	@Test
	public void concat() {
		List<String> result = queryFactory
				.select(member.username.concat("_").concat(member.age.stringValue()))
				.from(member)
				.where(member.username.eq("member1"))
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	// projection result return - basic
	@Test
	public void simpleProjection() {
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
				.fetch();

		System.out.println("result = " + result);
	}

	@Test
	public void tupleProjection() {
		List<Tuple> result = queryFactory
				.select(member.username, member.age)
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			String username = tuple.get(member.username);
			Integer age = tuple.get(member.age);

			System.out.println("username = " + username);
			System.out.println("age = " + age);
		}
	}

	// projection result return - DTO
	@Test
	public void findDtoBySetter() {
		List<MemberDto> result = queryFactory
				.select(Projections.bean(MemberDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void findDtoByField() {
		List<MemberDto> result = queryFactory
				.select(Projections.fields(MemberDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void findUserDtoByField() {
		List<UserDto> result = queryFactory
				.select(Projections.fields(UserDto.class,
						member.username.as("name"),
						ExpressionUtils.as(member.age, "age1")))
				.from(member)
				.fetch();
	}

	@Test
	public void findUserDtoByField2() {
		QMember memberSub = new QMember("memberSub");

		List<UserDto> result = queryFactory
				.select(Projections.fields(UserDto.class,
						member.username.as("name"),
						ExpressionUtils.as(JPAExpressions
							.select(memberSub.age.max())
							.from(memberSub), "age")
				))
				.from(member)
				.fetch();
	}

	@Test
	public void findDtoByConstructor() {
		List<MemberDto> result = queryFactory
				.select(Projections.constructor(MemberDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	// projection result return - @QueryProjection
	@Test
	public void findDtoByQueryProjection(){
		List<MemberDto> result = queryFactory
				.select(new QMemberDto(member.username, member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}


	// Dynamic query - BooleanBuilder
	@Test
	public void dynamicQuery_BooleanBuilder() {
		String usernameParam = "member1";
		Integer ageParam = 10;      // where member0_.username=? and member0_.age=?
		//Integer ageParam = null;  // where member0_.username=?

		List<Member> result = searchMember1(usernameParam, ageParam);

		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember1(String usernameCond, Integer ageCond) {
		//BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond)); // username not null
		BooleanBuilder builder = new BooleanBuilder();
		if (usernameCond != null) {
			builder.and(member.username.eq(usernameCond));
		}

		if (ageCond != null ) {
			builder.and(member.age.eq(ageCond));
		}

		return queryFactory
				.selectFrom(member)
				.where(builder)
				.fetch();
	}

	// Dynamic query - where multi parameter
	@Test
	public void dynamicQuery_WhereParam() {
		String usernameParam = "member1";
		Integer ageParam = 10;      // where member0_.username=? and member0_.age=?
		//Integer ageParam = null;  // where member0_.username=?

		List<Member> result = searchMember2(usernameParam, ageParam);

		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember2(String usernameCond, Integer ageCond) {
		return queryFactory
				.selectFrom(member)
				.where(usernameEq(usernameCond), ageEq(ageCond)) // if param in where null, the param is ignored
				//.where(allEq(usernameCond,ageCond))
				.fetch();
	}

	private BooleanExpression usernameEq(String usernameCond) {
		return usernameCond == null ? null : member.username.eq(usernameCond);
	}

	private BooleanExpression ageEq(Integer ageCond) {
		return ageCond == null ? null : member.age.eq(ageCond);
	}

	// advertisement state: isErviceable

	private BooleanExpression allEq(String usernameCond, Integer ageCond) {
		// null check
		return usernameEq(usernameCond).and(ageEq(ageCond));
	}

	// Bulk operation for update and remove
	@Test
	//@Commit
	public void bulkUpdate() {
		// member1 = 10 -> DB & PersistentContext : member1
		// member2 = 20 -> DB & PersistentContext : member2
		// member3 = 30 -> DB & PersistentContext : member3
		// member4 = 40 -> DB & PersistentContext : member4

		long count = queryFactory
				.update(member)
				.set(member.username, "비회원")
				.where(member.age.lt(28))
				.execute();

		// member1 = 10 -> DB 비회원    / PersistentContext member1
		// member2 = 20 -> DB 비회원    / PersistentContext member2
		// member3 = 30 -> DB member3  / PersistentContext member3
		// member4 = 40 -> DB member4  / PersistentContext member4

		em.flush();
		em.clear();

		List<Member> result = queryFactory
				.selectFrom(member)
				.fetch();

		for (Member member : result) {
			System.out.println("member1 = " + member);
		}
	}

	@Test
	public void bulkAdd() {
		long count = queryFactory
				.update(member)
				.set(member.age, member.age.add(1)) // add(-1) for minus
				.execute();
	}

	@Test
	public void bulkDelete() {
		queryFactory
				.delete(member)
				.where(member.age.gt(18))
				.execute();
	}

	// SQL function call
	@Test
	public void sqlFunction() {
		List<String> result = queryFactory
				.select(Expressions.stringTemplate(
						"function('replace', {0}, {1}, {2})",
						member.username, "member", "m"))
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void sqlFunction2() {
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
				.where(//member.username.eq(Expressions.stringTemplate("function('lower, {0})", member.username)))
						member.username.eq(member.username.lower())) // querydsl provides functions that ANSI standard does.
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}
}
