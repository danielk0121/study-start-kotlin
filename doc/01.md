
---
회원, 영화 api 를 개발할꺼야  
스프링부트, 코틀린, 그레이들 사용  
회원과 관심 영화는 1대n 관계  
jpa 사용  
간략한 crud api 를 코드로 작성해줘  
맵스트럭츠 사용하고  
코드는 되도록 유행하는 코트린 스타일로 작성  

---

요청하신 스택(Spring Boot, Kotlin, Gradle, JPA, MapStruct)을 사용하여 회원(Member)과 관심 영화(FavoriteMovie)의 1:N 관계를 포함한 CRUD API를 작성해 드릴게요.

최신 코틀린 스타일을 반영하여 **Constructor Injection**, **Data Classes**, 그리고 **Extension Functions** 등을 활용했습니다.

---

### 1. Build.gradle.kts 설정
MapStruct와 Kotlin을 함께 사용할 때는 `kapt` 설정이 중요합니다.

~~~kotlin
plugins {
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
    kotlin("kapt") version "1.9.22"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // MapStruct
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    kapt("org.mapstruct:mapstruct-processor:1.5.5.Final")

    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
~~~

---

### 2. Domain Entities (1:N 관계)
JPA 엔티티는 가급적 `open` 키워드가 필요하므로 `all-open` 플러그인을 사용하거나 일반 클래스로 선언합니다.

~~~kotlin
@Entity
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    var name: String,
    
    var email: String,

    @OneToMany(mappedBy = "member", cascade = [CascadeType.ALL], orphanRemoval = true)
    var favoriteMovies: MutableList<FavoriteMovie> = mutableListOf()
)

@Entity
class FavoriteMovie(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    var title: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    var member: Member? = null
)
~~~

---

### 3. DTO & MapStruct Mapper
데이터 전송 객체와 매퍼 설정입니다.

~~~kotlin
// DTOs
data class MemberRequest(val name: String, val email: String)
data class MemberResponse(val id: Long, val name: String, val email: String, val movies: List<MovieResponse>)
data class MovieResponse(val id: Long, val title: String)

// Mapper
@Mapper(componentModel = "spring")
interface MemberMapper {
    @Mapping(source = "favoriteMovies", target = "movies")
    fun toResponse(member: Member): MemberResponse
    
    fun toMovieResponse(movie: FavoriteMovie): MovieResponse
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "favoriteMovies", ignore = true)
    fun toEntity(request: MemberRequest): Member
}
~~~

---

### 4. Service & Controller
코틀린의 간결한 문법을 활용한 로직입니다.

~~~kotlin
@Service
@Transactional
class MemberService(
    private val memberRepository: MemberRepository,
    private val memberMapper: MemberMapper
) {
    fun createMember(request: MemberRequest): MemberResponse {
        val member = memberMapper.toEntity(request)
        return memberRepository.save(member).let { memberMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getMember(id: Long): MemberResponse =
        memberRepository.findByIdOrNull(id)?.let { memberMapper.toResponse(it) }
            ?: throw NoSuchElementException("Member not found")

    fun updateMember(id: Long, request: MemberRequest) {
        val member = memberRepository.findByIdOrNull(id) ?: throw NoSuchElementException()
        member.name = request.name
        member.email = request.email
    }

    fun deleteMember(id: Long) = memberRepository.deleteById(id)
}

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @PostMapping
    fun create(@RequestBody request: MemberRequest) = ResponseEntity.ok(memberService.createMember(request))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ResponseEntity.ok(memberService.getMember(id))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: MemberRequest): ResponseEntity<Unit> {
        memberService.updateMember(id, request)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> {
        memberService.deleteMember(id)
        return ResponseEntity.noContent().build()
    }
}
~~~

---

### 💡 주요 포인트
1.  **findByIdOrNull**: `spring-data-jpa`에서 제공하는 코틀린 확장 함수를 사용하여 `Optional` 대신 `null`을 직관적으로 다룹니다.
2.  **MutableList**: JPA의 1:N 관계 관리를 위해 `mutableListOf`를 사용했습니다.
3.  **MapStruct**: `kapt`를 통해 컴파일 타임에 매핑 구현체를 자동 생성합니다.
4.  **Let & Elvis**: 코틀린 특유의 scope function을 사용하여 코드를 간결하게 유지했습니다.

더 구체적인 비즈니스 로직(예: 관심 영화 추가 API)이 필요하시면 말씀해 주세요!
