package study.querydsl.dto;

import lombok.Data;
import org.springframework.objenesis.SpringObjenesis;

@Data
public class MemberSearchCondition {

	private String username;
	private String teamName;
	private Integer ageGoe;
	private Integer ageLoe;


}
