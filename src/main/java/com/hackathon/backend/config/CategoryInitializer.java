package com.hackathon.backend.config;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.repository.CategoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 기본 카테고리 시드.
 *
 * <p>기동할 때마다 "이름이 없는 것만" 넣기 때문에 재기동해도 중복되지 않고, 사용자가 나중에 이모지/색/순서를
 * 바꿔놓아도 덮어쓰지 않는다. 선물 기록 자체(데모 더미 데이터)는 넣지 않는다 — 여기서 만드는 건
 * 서비스가 동작하려면 반드시 있어야 하는 마스터 데이터뿐이다.</p>
 *
 * <p>카테고리를 추가하려면 이 파일을 고칠 필요 없이 {@code POST /api/categories} 를 호출하거나
 * {@code INSERT INTO categories (name, emoji, color, display_order, active) VALUES ('여행·체험','✈️','mint',80,true);}
 * 한 줄이면 된다.</p>
 */
@Configuration
public class CategoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(CategoryInitializer.class);

    private record Seed(String name, String emoji, String color, int displayOrder) {
    }

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("디저트", "🍰", "mint", 10),
            new Seed("꽃·식물", "💐", "pink", 20),
            new Seed("부조금", "💌", "blue", 30),
            new Seed("패션·잡화", "👜", "gold", 40),
            new Seed("상품권", "🎫", "mint", 50),
            new Seed("생활용품", "🕯️", "pink", 60),
            new Seed("기타", "🎁", "blue", 70)
    );

    @Bean
    @Order(1) // 다른 시드(DemoDataInitializer)가 카테고리를 참조하므로 가장 먼저 실행한다.
    public ApplicationRunner categorySeedRunner(CategoryRepository categoryRepository) {
        return args -> {
            List<Category> missing = DEFAULTS.stream()
                    .filter(seed -> !categoryRepository.existsByName(seed.name()))
                    .map(seed -> new Category(seed.name(), seed.emoji(), seed.color(), seed.displayOrder(), true))
                    .toList();
            if (missing.isEmpty()) {
                return;
            }
            categoryRepository.saveAll(missing);
            log.info("기본 카테고리 {}건 초기화: {}", missing.size(), missing.stream().map(Category::getName).toList());
        };
    }
}
