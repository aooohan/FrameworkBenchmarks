package benchmark.controller;

import benchmark.model.Fortune;
import benchmark.model.World;
import benchmark.repository.ReactiveFortuneRepository;
import benchmark.repository.ReactiveWorldRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import views.fortunes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Comparator.comparing;

@Requires(beans = {ReactiveFortuneRepository.class, ReactiveWorldRepository.class})
@Controller
public class ReactiveBenchmarkController extends AbstractBenchmarkController {

    private final ReactiveWorldRepository worldRepository;
    private final ReactiveFortuneRepository fortuneRepository;

    public ReactiveBenchmarkController(ReactiveWorldRepository worldRepository,
                                       ReactiveFortuneRepository fortuneRepository) {
        this.worldRepository = worldRepository;
        this.fortuneRepository = fortuneRepository;
    }

    @Get("/prepare-data-for-test")
    public Mono<Void> prepareDataForTest() {
        return Mono.from(worldRepository.initDb(createWords())).then(Mono.from(fortuneRepository.initDb(createFortunes())));
    }

    // https://github.com/TechEmpower/FrameworkBenchmarks/wiki/Project-Information-Framework-Tests-Overview#single-database-query
    @Get("/db")
    @SingleResult
    public Publisher<World> db() {
        return worldRepository.findById(randomId());
    }

    // https://github.com/TechEmpower/FrameworkBenchmarks/wiki/Project-Information-Framework-Tests-Overview#multiple-database-queries
    @Get("/queries")
    @SingleResult
    public Publisher<List<World>> queries(@QueryValue String queries) {
        int count = parseQueryCount(queries);
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(randomId());
        }
        return worldRepository.findByIds(ids);
    }

    // https://github.com/TechEmpower/FrameworkBenchmarks/wiki/Project-Information-Framework-Tests-Overview#fortunes
    @Get(value = "/fortunes", produces = "text/html;charset=utf-8")
    @SingleResult
    public Mono<HttpResponse<String>> fortune() {
        return Mono.from(fortuneRepository.findAll()).map(fortuneList -> {
            List<Fortune> all = new ArrayList<>(fortuneList.size() + 1);
            all.add(new Fortune(0, "Additional fortune added at request time."));
            all.addAll(fortuneList);
            all.sort(comparing(Fortune::getMessage));
            String body = fortunes.template(all).render().toString();
            return HttpResponse.ok(body).contentType("text/html;charset=utf-8");
        });
    }

    // https://github.com/TechEmpower/FrameworkBenchmarks/wiki/Project-Information-Framework-Tests-Overview#database-updates
    @Get("/updates")
    @SingleResult
    public Publisher<List<World>> updates(@QueryValue String queries) {
        return Flux.from(queries(queries)).flatMap(worlds -> {
            for (World world : worlds) {
                world.setRandomNumber(randomWorldNumber());
            }
            worlds.sort(Comparator.comparingInt(World::getId)); // Avoid deadlock
            return Mono.from(worldRepository.updateAll(worlds)).thenReturn(worlds);
        });
    }

}
