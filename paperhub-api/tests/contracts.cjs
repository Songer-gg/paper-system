const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../..");
const ts = require(
  path.join(root, "paperhub-frontend/node_modules/typescript"),
);

// 内存编译契约与 Mock，测试不输出 JS，不需要安装第二份依赖。
function loader() {
  const cache = new Map();
  function load(file) {
    const full = path.resolve(file);
    if (cache.has(full)) return cache.get(full).exports;
    const module = { exports: {} };
    cache.set(full, module);
    const source = ts.transpileModule(fs.readFileSync(full, "utf8"), {
      compilerOptions: {
        target: ts.ScriptTarget.ES2022,
        module: ts.ModuleKind.CommonJS,
      },
    }).outputText;
    function resolve(name) {
      const candidate = name.startsWith("@/")
        ? path.join(root, "paperhub-frontend/src", name.slice(2))
        : path.resolve(path.dirname(full), name);
      return load(candidate.endsWith(".ts") ? candidate : candidate + ".ts");
    }
    new Function("require", "module", "exports", source)(
      resolve,
      module,
      module.exports,
    );
    return module.exports;
  }
  return load;
}

function storage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
}

async function main() {
  const load = loader();
  const api = (file) => load(path.join(root, "paperhub-api/ts", file + ".ts"));
  const calls = [];
  const fake = new Proxy(
    {},
    {
      get:
        (_, method) =>
        (...args) => {
          calls.push({ method, args });
          return Promise.resolve(undefined);
        },
    },
  );
  const library = api("modules/library");
  const profile = api("modules/profile");
  const admin = api("modules/admin");
  const reading = api("modules/reading");
  await library.downloadPaper(fake, 1);
  assert.deepEqual(calls.pop(), {
    method: "download",
    args: ["/papers/1/download", "POST"],
  });
  await library.getPaperFile(fake, 1);
  assert.deepEqual(calls.pop(), {
    method: "download",
    args: ["/papers/1/file", "GET"],
  });
  await profile.updateAvatar(fake, new File(["avatar"], "avatar.png"));
  assert.equal(calls.pop().args[2], "PUT");
  await library.submitPaper(fake, new File(["%PDF-1.7"], "paper.pdf"), {
    title: "paper",
  });
  const upload = calls.pop();
  assert.equal(upload.args[2], "POST");
  assert.equal(upload.args[1].get("payload").type, "application/json");
  assert.deepEqual(JSON.parse(await upload.args[1].get("payload").text()), {
    title: "paper",
  });
  await admin.getUsers(fake, {
    keyword: "",
    status: "all",
    dateRange: ["2026-01-01", "2026-11-06"],
  });
  assert.deepEqual(calls.pop().args[1], {
    keyword: "",
    status: "all",
    dateFrom: "2026-01-01",
    dateTo: "2026-11-06",
  });
  await admin.approvePaper(fake, 1, "review", false);
  assert.equal(calls.pop().args[1].copyrightConfirmed, false);
  await reading.updateNote(fake, 1, { paperId: 2, content: "updated" });
  assert.equal(calls.pop().args[1].paperId, 2);

  const { createFetchClient } = api("fetch-client");
  let unauthorized = 0;
  let captured;
  const client = createFetchClient({
    getToken: () => "test-token",
    onUnauthorized: () => unauthorized++,
    fetch: async (url, init) => {
      captured = { url, init };
      return new Response(
        JSON.stringify({ code: 0, message: "ok", data: 42 }),
        { headers: { "content-type": "application/json" } },
      );
    },
  });
  assert.equal(await client.get("/answer", { q: "a b", empty: null }), 42);
  assert.equal(captured.url, "/api/v1/answer?q=a+b");
  assert.equal(captured.init.headers.get("Authorization"), "Bearer test-token");
  const denied = createFetchClient({
    getToken: () => null,
    onUnauthorized: () => unauthorized++,
    fetch: async () =>
      new Response(
        JSON.stringify({ code: 1001, message: "expired", data: null }),
        { status: 401, headers: { "content-type": "application/json" } },
      ),
  });
  await assert.rejects(
    denied.download("/papers/1/file", "GET"),
    (error) => error.code === 1001,
  );
  assert.equal(unauthorized, 1);
  const pdfClient = createFetchClient({
    getToken: () => null,
    fetch: async () =>
      new Response("%PDF-1.7", {
        headers: { "content-type": "application/pdf" },
      }),
  });
  assert.equal(
    (await pdfClient.download("/papers/1/file", "GET")).type,
    "application/pdf",
  );

  global.localStorage = storage();
  global.sessionStorage = storage();
  localStorage.setItem(
    "paperhub_auth",
    JSON.stringify({ id: 1, role: "user" }),
  );
  const front = (file) =>
    load(path.join(root, "paperhub-frontend/src", file + ".ts"));
  const mine = front("services/myLibraryService");
  const personal = front("services/userDataService");
  const manager = front("services/adminService");
  const own = (await mine.getMyPapers()).sharedPapers.find(
    (paper) => paper.reviewStatus === "approved",
  );
  assert.ok(own);
  await mine.changeReviewStatus(own.id, "unshare");
  assert.equal(
    (await mine.getMyPapers()).sharedPapers.find((p) => p.id === own.id)
      .offlineReason,
    "withdrawn",
  );
  await assert.rejects(manager.restorePaper(own.id));
  await mine.changeReviewStatus(own.id, "resubmit");
  assert.equal(
    (await mine.getMyPapers()).sharedPapers.find((p) => p.id === own.id)
      .reviewStatus,
    "pending",
  );
  const visible = (
    await front("services/paperService").searchSharedPapers({
      page: 1,
      pageSize: 10,
    })
  ).list[0];
  const note = await personal.addNote({
    paperId: visible.id,
    title: "private note",
    content: "my content",
  });
  await personal.updateReadingStatus(visible.id, "paused");
  const added = (await personal.getReadingRecords()).find(
    (r) => r.paperId === visible.id,
  ).addedAt;
  await personal.addToReading(visible.id);
  assert.equal(
    (await personal.getReadingRecords()).find((r) => r.paperId === visible.id)
      .addedAt,
    added,
  );
  localStorage.setItem(
    "paperhub_auth",
    JSON.stringify({ id: 2, role: "user" }),
  );
  assert.equal((await personal.getNotes()).length, 0);
  assert.equal((await personal.getReadingRecords()).length, 0);
  assert.equal((await personal.getFavorites()).length, 0);
  localStorage.setItem(
    "paperhub_auth",
    JSON.stringify({ id: 101, role: "admin" }),
  );
  assert.ok(
    (await manager.getUserNotes(1)).some((item) => item.id === note.id),
  );
  await manager.deletePaper(visible.id);
  localStorage.setItem(
    "paperhub_auth",
    JSON.stringify({ id: 1, role: "user" }),
  );
  const retained = (await personal.getNotes()).find(
    (item) => item.id === note.id,
  );
  assert.equal(retained.content, "my content");
  assert.equal(retained.paperTitle, visible.title);
  assert.equal(retained.paperAvailable, false);
  assert.ok((await manager.getLogs("敏感数据访问")).length);
  assert.equal(
    (
      fs
        .readFileSync(path.join(root, "paperhub-api/api-spec.md"), "utf8")
        .match(/^\|\s+[A-Z]\d+\s+\|/gm) ?? []
    ).length,
    67,
  );
  console.log(
    "PASS: transport contracts, JSON/PDF errors, user isolation, withdrawal and retained notes",
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
