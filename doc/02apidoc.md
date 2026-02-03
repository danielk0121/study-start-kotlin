
---
추천 기술 스택인 epages 가 좋아보이네
코드 작성 해봐

---

`epages/restdocs-api-spec`을 활용하면 테스트 코드만으로도 Swagger UI에서 확인 가능한 **OpenAPI 3 스펙**을 뽑아낼 수 있습니다.

회원 API를 기준으로 전체적인 설정과 테스트 코드 예시를 작성해 드릴게요.

---

### 1. build.gradle.kts 설정
먼저 플러그인과 의존성을 추가해야 합니다.

~~~kotlin
plugins {
    // ... 기존 플러그인 생략
    id("com.epages.restdocs-api-spec") version "0.19.2"
}

dependencies {
    // RestDocs & MockMvc
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    // epages wrapper
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.2")
}

// Swagger UI용 OpenAPI3 스펙 설정
openapi3 {
    setServer("http://localhost:8080")
    title = "Movie Member API"
    description = "회원 및 관심 영화 관리 API"
    version = "1.0.0"
    format = "yaml" // 또는 "json"
}
~~~

---

### 2. Base Test 클래스 설정 (중복 코드 방지)
테스트 마다 공통적으로 들어가는 설정을 별도의 클래스로 뺍니다.

~~~kotlin
@ExtendWith(RestDocumentationExtension::class)
@SpringBootTest
abstract class RestDocsTest {
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(MockMvcRestDocumentation.documentationConfiguration(provider))
            .build()
    }
}
~~~

---

### 3. 회원 API 테스트 및 문서화 코드
이제 실제 컨트롤러 테스트를 작성합니다. `MockMvcRestDocumentationWrapper`를 사용하는 것이 핵심입니다.

~~~kotlin
class MemberControllerTest : RestDocsTest() {

    @MockBean
    private lateinit var memberService: MemberService

    @Test
    fun `회원 생성 API 문서화`() {
        // Given
        val request = MemberRequest(name = "Gemini", email = "gemini@example.com")
        val response = MemberResponse(id = 1L, name = "Gemini", email = "gemini@example.com", movies = emptyList())
        
        given(memberService.createMember(any())).willReturn(response)

        // When & Then
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ObjectMapper().writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andDo(
                MockMvcRestDocumentationWrapper.document(
                    identifier = "member-create",
                    resourceDetails = ResourceSnippetParameters.builder()
                        .tag("Member")
                        .summary("새로운 회원 생성")
                        .description("이름과 이메일을 입력받아 회원을 등록합니다."),
                    requestFields(
                        fieldWithPath("name").description("회원 이름"),
                        fieldWithPath("email").description("회원 이메일")
                    ),
                    responseFields(
                        fieldWithPath("id").description("생성된 회원 ID"),
                        fieldWithPath("name").description("회원 이름"),
                        fieldWithPath("email").description("회원 이메일"),
                        fieldWithPath("movies").description("관심 영화 목록 (초기 빈 배열)")
                    )
                )
            )
    }
}
~~~

---

### 4. 문서 생성 및 확인 방법

이 방식은 아래의 과정을 거쳐 문서가 완성됩니다.



1.  **테스트 실행**: `./gradlew test` 를 실행하면 테스트가 통과하면서 `build/generated-snippets`에 문서 조각들이 생깁니다.
2.  **OpenAPI 스펙 생성**: `./gradlew openapi3` 명령어를 실행하면 `build/api-spec/openapi3.yaml` 파일이 생성됩니다.
3.  **Swagger UI 연결**: 해당 `.yaml` 파일을 Swagger UI(예: Docker로 띄우거나 Static Resource로 서빙)에 연결하면 끝입니다.

---

### 💡 팁: 왜 이렇게 하나요?
- **비즈니스 코드 보호**: 엔티티나 컨트롤러에 `@Schema`, `@ApiResponse` 같은 어노테이션이 도배되지 않습니다.
- **강제성**: 테스트를 짜지 않으면 문서가 나오지 않으므로, 개발자가 귀찮아서 문서를 업데이트 안 하는 일을 방지할 수 있습니다.

이 설정으로 진행해 보시겠어요? 막히는 부분이 있다면 알려주세요!


