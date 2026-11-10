package org.financelab.review;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.financelab.budget.adapter.postgres.PostgresBudgetStore;
import org.financelab.budget.application.BudgetStore.*;
import org.financelab.budget.application.SaveResult;
import org.financelab.budget.domain.BudgetAmount;
import org.financelab.budget.domain.BudgetLine;
import org.financelab.orm.JdbcTransactions;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class PerformanceReviewMain {
    static final UUID OWNER=UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final YearMonth MONTH=YearMonth.of(2040,1);
    static final Path EVIDENCE=Path.of(System.getenv("REVIEW_EVIDENCE_DIR"));
    static final String URL=System.getenv("APP_BASE_URL"), CONTAINER=System.getenv("REVIEW_DATABASE_CONTAINER");
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final Map<String,Object> RESULTS=new LinkedHashMap<>();
    static final int WARM=5, N=30;
    static Process restarted;
    static CountingPool pool;
    static PostgresBudgetStore store;

    public static void main(String[] args) throws Exception {
        check(CONTAINER.matches("[0-9a-f]{64}"),"Unexpected fixture container identity");
        check(docker("inspect","--format","{{ index .Config.Labels \"org.financelab.review\" }}",CONTAINER).trim().equals("step-7"),"Fixture ownership");
        RESULTS.put("schema_version",1);
        RESULTS.put("observation_date","2026-11-08");
        RESULTS.put("method",Map.of("warmups",WARM,"samples",N,"percentile","nearest rank",
                "scope","Exploratory local single-account measurements; no production SLO or capacity claim."));
        try {
            pool=new CountingPool(); store=new PostgresBudgetStore(new JdbcTransactions(pool),f -> {});
            store.verifyMappings();
            try(var c=pool.getConnection();var s=c.createStatement()) {
                var defaults=new LinkedHashMap<String,Object>();
                for(String name:List.of("statement_timeout","lock_timeout"))try(var r=s.executeQuery("SHOW "+name)) {
                    r.next();defaults.put(name,r.getString(1));
                }
                defaults.put("jdbc_network_timeout_ms",c.getNetworkTimeout());
                try(var p=c.prepareStatement("SELECT 1")){defaults.put("jdbc_query_timeout_s",p.getQueryTimeout());}
                RESULTS.put("database_wait_defaults",defaults);
            }
            var profiles=new ArrayList<Object>();
            profiles.add(profile("default",OWNER,10,12,24));
            profiles.add(profile("expanded",UUID.randomUUID(),50,12,24));
            profiles.add(profile("long_history",UUID.randomUUID(),10,1,240));
            RESULTS.put("persistence_profiles",profiles);checkpoint();
            try(var pw=Playwright.create();var browser=pw.chromium().launch()) {
                RESULTS.put("page_loads",pageLoads(pw,browser));checkpoint();
                var browserProfiles=new ArrayList<Object>();
                browserProfiles.add(browserProfile(browser,"default",10));
                expand(OWNER,50);
                var v=store.current(OWNER,MONTH);
                saved(store.save(OWNER,command(v,7000)));
                browserProfiles.add(browserProfile(browser,"expanded",50));
                RESULTS.put("browser_profiles",browserProfiles);checkpoint();
                RESULTS.put("dump_overlap",dumpOverlap());checkpoint();
                RESULTS.put("resilience",resilience(pw,browser));checkpoint();
            }
            RESULTS.put("completed",true);
            Files.writeString(EVIDENCE.resolve("measurements.json"),JSON.writeValueAsString(RESULTS));
            System.out.println("STEP7 all measurements complete");
        } finally {
            if(restarted!=null) {
                restarted.destroy();
                if(!restarted.waitFor(15,TimeUnit.SECONDS)){restarted.destroyForcibly();restarted.waitFor();}
            }
            if(pool!=null)pool.close();
        }
    }

    static Map<String,Object> profile(String label,UUID owner,int categories,int months,int revisions) throws Exception {
        saved(store.initializeCatalog(owner));expand(owner,categories);
        for(int m=0;m<months;m++)for(int revision=0;revision<revisions;revision++) {
            var v=store.current(owner,MONTH.plusMonths(m));
            saved(store.save(owner,command(v,revision+1)));
        }
        var v=store.current(owner,MONTH);
        var metrics=new LinkedHashMap<String,Object>();
        metrics.put("catalog",measure(() -> store.catalog(owner).orElseThrow()));
        metrics.put("current",measure(() -> store.current(owner,MONTH)));
        metrics.put("history_list",measure(() -> store.revisions(owner,v.budget().id())));
        metrics.put("first_revision",measure(() -> store.revision(owner,v.budget().id(),1).orElseThrow()));
        var writes=new Samples();
        for(int i=0;i<WARM+N;i++) {
            var current=store.current(owner,MONTH);
            var command=command(current,1000+i);
            pool.count.set(0);long start=System.nanoTime();
            saved(store.save(owner,command));
            double elapsed=ms(start);int queries=pool.count.get();
            if(i>=WARM)writes.add(elapsed,queries);
        }
        metrics.put("atomic_save",writes.json());
        check(pool.getHikariPoolMXBean().getActiveConnections()==0,"Leaked benchmark connection");
        System.out.println("STEP7 persistence "+label+" complete");
        return Map.of("label",label,"categories",categories,"months_seeded",months,
                "revisions_per_month_at_read_sample",revisions,
                "selected_month_version_after_write_samples",store.current(owner,MONTH).version(),"metrics",metrics);
    }
    static Object measure(Supplier<?> work) {
        var sample=new Samples();
        for(int i=0;i<WARM+N;i++) {
            pool.count.set(0);long start=System.nanoTime();Object result=work.get();
            double elapsed=ms(start);int queries=pool.count.get();check(result!=null,"Missing benchmark result");
            if(i>=WARM)sample.add(elapsed,queries);
        }
        return sample.json();
    }
    static void expand(UUID owner,int count) {
        var view=store.catalog(owner).orElseThrow();var catalog=view.catalog();
        while(catalog.categories().size()<count)catalog=catalog.add("Synthetic category "+(catalog.categories().size()+1));
        if(!catalog.equals(view.catalog()))saved(store.saveCatalog(owner,view.revision().version(),catalog));
    }
    static SaveBudget command(BudgetView v,int sequence) {
        var b=v.budget();int[] index={0};
        return new SaveBudget(b.month(),v.version(),v.catalog().revision().version(),
                BudgetAmount.set(new BigDecimal(100000+sequence)),
                b.lines().stream().filter(l->l.kind()==BudgetLine.Kind.SPENDING)
                        .map(l->new SpendingValue(l.categoryId().orElseThrow(),
                                BudgetAmount.set(BigDecimal.valueOf(100L+(++index[0])+sequence,2)))).toList(),
                BudgetAmount.set(new BigDecimal("1000.00")));
    }
    static void saved(Object result){check(result instanceof SaveResult.Saved<?>,"Expected one confirmed save");}

    static Object pageLoads(Playwright pw,Browser browser) {
        var guest=new Samples();var cloud=new Samples();var login=new Samples();
        for(int i=0;i<5;i++) {
            try(var c=context(browser)) {
                var p=c.newPage();long start=System.nanoTime();p.navigate(URL+"local");ready(p);
                guest.add(ms(start),null);
            }
            try(var c=context(browser)) {
                long auth=authenticate(c.request());login.add(auth/1e6,null);
                var p=c.newPage();long start=System.nanoTime();p.navigate(URL);ready(p);
                cloud.add(ms(start),null);
            }
        }
        return Map.of("guest_fresh_context",guest.json(),"account_fresh_context_excluding_login",cloud.json(),
                "valid_login_post",login.json(),"browser_process_startup_included",false,
                "note","Five fresh-context samples; browser process and local DNS/TLS environment already warm.");
    }
    static Object browserProfile(Browser browser,String label,int categories) {
        Map<String,Object> expected;
        try(var c=context(browser)) {
            authenticate(c.request());var p=c.newPage();p.navigate(URL);ready(p);
            expected=object(rpc(p,"month",Map.of("month",MONTH.toString())));
            check(((List<?>)expected.get("lines")).size()==categories,"Browser category shape");
            var reads=new Samples();
            for(int i=0;i<WARM+N;i++) {
                long start=System.nanoTime();var result=object(rpc(p,"month",Map.of("month",MONTH.toString())));
                double elapsed=ms(start);check(result.get("version").equals(expected.get("version")),"Unexpected read revision");
                if(i>=WARM)reads.add(elapsed,null);
            }
            var writes=new Samples();
            for(int i=0;i<WARM+N;i++) {
                var current=object(rpc(p,"month",Map.of("month",MONTH.toString())));
                var payload=new LinkedHashMap<>(current);payload.put("month",MONTH.toString());
                payload.put("income",Integer.toString(200000+i));
                long start=System.nanoTime();var reply=object(rpc(p,"save",payload));double elapsed=ms(start);
                check(Boolean.TRUE.equals(reply.get("saved")),"Browser write failed");
                if(i>=WARM)writes.add(elapsed,null);
            }
            var result=new LinkedHashMap<String,Object>();
            result.put("label",label);result.put("categories",categories);
            result.put("read_revision",expected.get("version"));result.put("warm_month_rpc",reads.json());
            result.put("save_rpc_excluding_followup_read",writes.json());
            result.put("four_sessions",concurrentReads(browser));
            System.out.println("STEP7 browser "+label+" complete");
            return result;
        }
    }
    static Object concurrentReads(Browser browser) {
        var contexts=new ArrayList<BrowserContext>();var pages=new ArrayList<Page>();
        try {
            for(int i=0;i<4;i++){
                var c=context(browser);contexts.add(c);authenticate(c.request());
                var p=c.newPage();pages.add(p);p.navigate(URL);ready(p);
                for(int j=0;j<3;j++)check(!object(rpc(p,"month",Map.of("month",MONTH.toString()))).containsKey("error"),"Concurrent warmup");
            }
            long go=System.currentTimeMillis()+500;
            for(var p:pages)p.evaluate("""
                args => {
                  let root=document.querySelector('#planner');
                  while(root && typeof root.$server?.planner!=='function')root=root.parentElement;
                  window.reviewConcurrent={done:false};
                  (async()=>{
                    await new Promise(r=>setTimeout(r,Math.max(0,args.go-Date.now())));
                    const start=Date.now(),samples=[];let errors=0;
                    for(let i=0;i<args.n;i++){
                      const t=performance.now();
                      const value=JSON.parse(await root.$server.planner('month',JSON.stringify({month:args.month})));
                      samples.push(performance.now()-t);if(value.error)errors++;
                    }
                    window.reviewConcurrent={done:true,start,end:Date.now(),samples,errors};
                  })().catch(()=>window.reviewConcurrent={done:true,error:true});
                }
                """,Map.of("go",(double)go,"n",15,"month",MONTH.toString()));
            var combined=new Samples();long first=Long.MAX_VALUE,last=0,maxStart=0,minEnd=Long.MAX_VALUE;
            int errors=0;
            for(var p:pages){
                p.waitForFunction("window.reviewConcurrent.done");
                var result=object(p.evaluate("window.reviewConcurrent"));
                check(!Boolean.TRUE.equals(result.get("error")),"Concurrent RPC failure");
                long start=((Number)result.get("start")).longValue(),end=((Number)result.get("end")).longValue();
                first=Math.min(first,start);maxStart=Math.max(maxStart,start);last=Math.max(last,end);minEnd=Math.min(minEnd,end);
                errors+=((Number)result.get("errors")).intValue();
                for(Object sample:(List<?>)result.get("samples"))combined.add(((Number)sample).doubleValue(),null);
            }
            check(errors==0,"Unexpected concurrent errors");
            check(maxStart<minEnd,"Four session loops did not overlap");
            return Map.of("sessions",4,"reads_per_session",15,"total_reads",60,
                    "errors",errors,"overlapping_session_loops",true,"elapsed_ms",last-first,
                    "achieved_reads_per_second",60000.0/(last-first),"latency",combined.json(),
                    "note","Bounded closed loops, not a maximum-capacity or sustained-load measurement.");
        } finally {for(var c:contexts)c.close();}
    }

    static Object dumpOverlap() throws Exception {
        var destination=EVIDENCE.resolve("synthetic-review.dump");
        try {
            long start=System.nanoTime();
            var dump=new ProcessBuilder("docker","exec",CONTAINER,"pg_dump","-U","finance_backup",
                    "-d","finance_lab","--format=custom").redirectOutput(destination.toFile())
                    .redirectError(EVIDENCE.resolve("dump.log").toFile()).start();
            var ended=dump.onExit().thenApply(p->System.nanoTime());
            var reads=new Samples();
            while(dump.isAlive() && reads.values.size()<100) {
                long t=System.nanoTime();store.current(OWNER,MONTH);reads.add(ms(t),null);
            }
            check(dump.waitFor()==0,"Synthetic dump failed");long end=ended.get();
            long bytes=Files.size(destination);
            return Map.of("duration_ms",(end-start)/1e6,"archive_bytes",bytes,
                    "current_reads_started_while_dump_alive",reads.json(),
                    "scope","One local pg_dump under the backup role; no age encryption, upload, restore or provider checks.");
        } finally {Files.deleteIfExists(destination);}
    }

    static Object resilience(Playwright pw,Browser browser) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        try(var c=context(browser)) {
            authenticate(c.request());var p=c.newPage();p.navigate(URL);ready(p);
            var before=object(rpc(p,"month",Map.of("month",MONTH.toString())));
            var payload=new LinkedHashMap<>(before);payload.put("month",MONTH.toString());payload.put("income","77777,77");
            try(var lock=admin()) {
                lock.setAutoCommit(false);
                try(var statement=lock.prepareStatement("SELECT owner_id FROM budget.catalog_guard WHERE owner_id=? FOR UPDATE")){
                    statement.setObject(1,OWNER);statement.executeQuery().close();
                }
                p.evaluate("""
                    args=>{
                      let r=document.querySelector('#planner');while(r&&typeof r.$server?.planner!=='function')r=r.parentElement;
                      const start=performance.now();window.reviewBlocked={done:false};
                      r.$server.planner('save',JSON.stringify(args)).then(text=>
                        window.reviewBlocked={done:true,elapsed:performance.now()-start,value:JSON.parse(text)})
                        .catch(()=>window.reviewBlocked={done:true,error:true});
                    }
                    """,payload);
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                boolean waiting=false;
                while(System.nanoTime()<until){
                    try(var a=admin();var s=a.createStatement();var rows=s.executeQuery(
                            "SELECT count(*) FROM pg_stat_activity WHERE usename='finance_app' AND wait_event_type='Lock' AND query LIKE '%catalog_guard%'")){
                        rows.next();waiting=rows.getLong(1)>0;
                    }
                    if(waiting)break;Thread.sleep(25);
                }
                check(waiting,"Application did not reach the expected database lock");
                try {
                    p.waitForTimeout(6500);
                    check(Boolean.FALSE.equals(object(p.evaluate("window.reviewBlocked")).get("done")),"Blocked statement unexpectedly completed");
                } finally {lock.commit();}
            }
            p.waitForFunction("window.reviewBlocked.done");
            var blocked=object(p.evaluate("window.reviewBlocked"));
            check(Boolean.TRUE.equals(object(blocked.get("value")).get("saved")),"Save after lock release");
            var after=object(rpc(p,"month",Map.of("month",MONTH.toString())));
            check(((Number)after.get("version")).longValue()==((Number)before.get("version")).longValue()+1,"Lock probe duplicate save");
            result.put("held_catalog_lock",Map.of("observed_database_lock_wait",true,
                    "still_pending_after_ms",6500,"rpc_elapsed_ms",blocked.get("elapsed"),
                    "saved_after_release",true,"revisions_added",1,
                    "note","The five-second pool acquisition limit does not bound an executing statement."));
            System.out.println("STEP7 lock stall observed and released");

            String originalPort=docker("port",CONTAINER,"5432/tcp").trim();
            docker("stop","--time","2",CONTAINER);
            long failed=System.nanoTime();var unavailable=object(rpc(p,"month",Map.of("month",MONTH.toString())));
            double failedMs=ms(failed);check(unavailable.containsKey("error"),"Read unexpectedly succeeded with database stopped");
            var anonymous=pw.request().newContext(new APIRequest.NewContextOptions().setIgnoreHTTPSErrors(true));
            int guest;
            try{guest=anonymous.get(URL+"local").status();}finally{anonymous.dispose();}
            check(guest==200,"Guest route depends on stopped database");
            long restart=System.nanoTime();docker("start",CONTAINER);
            check(originalPort.equals(docker("port",CONTAINER,"5432/tcp").trim()),"Fixture changed the database endpoint");
            long ready=waitDatabase();int attempts=0;Map<String,Object> recovered=null;
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(System.nanoTime()<until){
                attempts++;var read=object(rpc(p,"month",Map.of("month",MONTH.toString())));
                if(!read.containsKey("error")){recovered=read;break;}Thread.sleep(100);
            }
            check(recovered!=null,"Application read did not recover within the probe window");
            check(recovered.get("version").equals(after.get("version"))
                    &&recovered.get("income").equals(after.get("income")),"Selected income/revision changed across database restart");
            result.put("database_stop_start",Map.of("failed_read",unavailable.get("error"),
                    "failed_read_ms",failedMs,"anonymous_guest_status_during_outage",guest,
                    "database_ready_ms",(ready-restart)/1e6,"first_successful_read_ms",ms(restart),
                    "explicit_recovery_read_attempts",attempts,"value_and_revision_preserved",true,
                    "storage","Dedicated disposable Docker volume retained across stop/start",
                    "note","Read retries are the review harness, not automatic write replay."));
            System.out.println("STEP7 database stop/start recovered");

            String localKey="finance-lab.guest.v1.2044-03",localValue="{\"income\":\"321\",\"investment\":\"0\",\"spending\":[\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"]}";
            p.evaluate("args=>localStorage.setItem(args[0],args[1])",List.of(localKey,localValue));
            String oldCookie=c.cookies(URL).stream().filter(x->x.name.equals("JSESSIONID"))
                    .map(x->"JSESSIONID="+x.value).findFirst().orElseThrow();
            long pid=Long.parseLong(System.getenv("REVIEW_JETTY_PID"));
            var handle=ProcessHandle.of(pid).orElseThrow();
            check(Arrays.asList(handle.info().arguments().orElse(new String[0]))
                    .contains(Path.of(System.getenv("REVIEW_APP_DIR"),"config/jetty-auth.xml").toString()),"Unexpected JVM target");
            check(handle.destroyForcibly(),"Fixture JVM was not killed");handle.onExit().get(15,TimeUnit.SECONDS);
            long jvmStart=System.nanoTime();
            restarted=new ProcessBuilder("bash",Path.of(System.getenv("REVIEW_APP_DIR"),"scripts/run.sh").toString())
                    .directory(Path.of(System.getenv("REVIEW_APP_DIR")).toFile()).redirectErrorStream(true)
                    .redirectOutput(EVIDENCE.resolve("jetty-restarted.log").toFile()).start();
            Files.writeString(EVIDENCE.resolve("restarted-jetty.pid"),Long.toString(restarted.pid()));
            waitLogin(pw);
            var replay=c.request().get(URL+"transfer",RequestOptions.create().setHeader("Cookie",oldCookie));
            check(replay.status()==401,"Old session survived JVM restart");
            authenticate(c.request());p.navigate(URL);ready(p);
            var finalView=object(rpc(p,"month",Map.of("month",MONTH.toString())));
            check(finalView.get("version").equals(after.get("version"))&&finalView.get("income").equals(after.get("income")),"JVM restart lost confirmed data");
            check(localValue.equals(p.evaluate("k=>localStorage.getItem(k)",localKey)),"Browser local state changed on JVM restart");
            result.put("jvm_kill_restart",Map.of("old_session_status",replay.status(),
                    "restart_to_authenticated_read_ms",ms(jvmStart),
                    "confirmed_value_and_revision_preserved",true,"browser_local_value_preserved",true,
                    "relogin_required",true,"scope","Explicit harness restart, not an automatic service-manager test."));
            System.out.println("STEP7 JVM kill/restart recovered");
        }
        return result;
    }

    static long waitDatabase() throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(System.nanoTime()<until){
            var p=new ProcessBuilder("docker","exec",CONTAINER,"pg_isready","-h","127.0.0.1","-U","postgres","-d","finance_lab")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(p.waitFor()==0)return System.nanoTime();Thread.sleep(100);
        }
        throw new IllegalStateException("Disposable database not ready");
    }
    static void waitLogin(Playwright pw) throws Exception {
        var api=pw.request().newContext(new APIRequest.NewContextOptions().setIgnoreHTTPSErrors(true));
        try {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
            while(System.nanoTime()<until){
                if(!restarted.isAlive())throw new IllegalStateException("Restarted fixture exited");
                try{if(api.get(URL+"login",RequestOptions.create().setTimeout(1000)).status()==200)return;}
                catch(PlaywrightException ignored){}
                Thread.sleep(100);
            }
            throw new IllegalStateException("Restarted login not ready");
        }finally{api.dispose();}
    }
    static Connection admin() throws SQLException {return DriverManager.getConnection(System.getenv("APP_DB_URL"),"postgres","synthetic-admin");}
    static String docker(String...args) throws Exception {
        var all=new ArrayList<String>();all.add("docker");all.addAll(List.of(args));
        var p=new ProcessBuilder(all).redirectErrorStream(true).start();
        String text=new String(p.getInputStream().readAllBytes());
        check(p.waitFor()==0,"Fixture Docker command failed");return text;
    }
    static BrowserContext context(Browser b){return b.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true).setLocale("en-US").setTimezoneId("Pacific/Honolulu"));}
    static long authenticate(APIRequestContext api){
        var response=api.get(URL+"login");
        var token=Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(response.text());
        check(token.find(),"Login CSRF missing");long start=System.nanoTime();
        var logged=api.post(URL+"login",RequestOptions.create().setMaxRedirects(0)
                .setHeader("Origin",System.getenv("APP_PUBLIC_ORIGIN"))
                .setForm(FormData.create().set("csrf",token.group(1)).set("username","synthetic-user").set("password","synthetic-test-password-only")));
        check(logged.status()==303,"Synthetic login failed");return System.nanoTime()-start;
    }
    static void ready(Page p){p.locator("#save").waitFor();p.waitForFunction("!document.querySelector('#save').disabled");}
    static Object rpc(Page p,String action,Map<String,?> data){
        return p.evaluate("""
            async args=>{
              let r=document.querySelector('#planner');while(r&&typeof r.$server?.planner!=='function')r=r.parentElement;
              if(!r)throw Error('Planner bridge unavailable');
              return JSON.parse(await r.$server.planner(args[0],JSON.stringify(args[1])));
            }
            """,List.of(action,data));
    }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object v){return (Map<String,Object>)v;}
    static double ms(long start){return (System.nanoTime()-start)/1e6;}
    static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void checkpoint() throws IOException {Files.writeString(EVIDENCE.resolve("partial-measurements.json"),JSON.writeValueAsString(RESULTS));}

    static final class Samples {
        final List<Double> values=new ArrayList<>();final List<Integer> queries=new ArrayList<>();
        void add(double value,Integer count){values.add(value);if(count!=null)queries.add(count);}
        Object json(){
            var sorted=values.stream().sorted().toList();var m=new LinkedHashMap<String,Object>();
            m.put("n",values.size());m.put("samples_ms",values);
            if(!sorted.isEmpty()){m.put("min_ms",sorted.getFirst());m.put("median_ms",quantile(sorted,.5));
                m.put("p95_ms",quantile(sorted,.95));m.put("max_ms",sorted.getLast());}
            if(!queries.isEmpty()){m.put("statement_counts",queries);m.put("min_statements",Collections.min(queries));m.put("max_statements",Collections.max(queries));}
            return m;
        }
        static double quantile(List<Double> v,double p){return v.get(Math.max(0,(int)Math.ceil(v.size()*p)-1));}
    }
    static final class CountingPool extends HikariDataSource {
        final ThreadLocal<Integer> count=ThreadLocal.withInitial(()->0);
        final Map<Connection,Connection> originals=Collections.synchronizedMap(new IdentityHashMap<>());
        CountingPool(){
            super(config());
        }
        static HikariConfig config(){
            var c=new HikariConfig();c.setJdbcUrl(System.getenv("APP_DB_URL"));c.setUsername("finance_app");
            c.setPassword("synthetic-runtime");c.setMaximumPoolSize(4);c.setConnectionTimeout(5000);return c;
        }
        @Override public Connection getConnection() throws SQLException {
            Connection original=super.getConnection();
            Connection wrapped=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("close"))originals.remove(proxy);
                Object value=invoke(method,original,args);
                if(method.getName().equals("prepareStatement")&&args[0] instanceof String sql&&value instanceof PreparedStatement statement){
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                        if(m.getName().startsWith("execute")&&!sql.startsWith("SET LOCAL"))count.set(count.get()+1);
                        return invoke(m,statement,a);
                    });
                }
                return value;
            });
            originals.put(wrapped,original);return wrapped;
        }
        @Override public void evictConnection(Connection c){super.evictConnection(originals.getOrDefault(c,c));}
        static Object invoke(Method method,Object target,Object[] args)throws Throwable{
            try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
        }
    }
}
