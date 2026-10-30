# -*- coding: utf-8 -*-
"""小样本 RAG 对照实验：同一批课程问题，「带检索」vs「裸模型」。

目的不是给系统打准确率，而是验证一件事：
**课程资料里的特定事实，模型本身不知道，接上检索后才答得出。**

依赖：Python 3.10+（仅标准库）、本机 Ollama（bge-m3 + 任一 chat 模型）。

用法：
    python tools/experiment/rag_ab.py --corpus tools/experiment/corpus

语料：`--corpus` 目录下所有 `*.txt`（从课件/讲义导出的纯文本，不入库、不提交）。
结果：默认写到 `tools/experiment/results/<时间戳>.json`。
"""
import argparse
import io
import json
import math
import os
import re
import time
import urllib.request

QUESTIONS = [
    ("这门课的实验报告里，数据库名字要按什么规则起？", "MYSPJ"),
    ("实验一用的是什么数据库管理系统和版本？", "SQL Server 2008"),
    ("实验一要求建立哪几个基本表？", "S P J SPJ"),
    ("实验报告除了截图还要写什么？", "错误原因 实验总结 心得体会"),
    ("这门课是哪位老师上的、哪一学期开的？", "张美琪 2025"),
    ("实验一到实验四的数据库命名要保持一致吗？", "一致"),
]


def post(base, path, payload, timeout=180):
    req = urllib.request.Request(
        base + path,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def embed(base, model, text):
    return post(base, "/api/embeddings", {"model": model, "prompt": text})["embedding"]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb + 1e-12)


def load_chunks(corpus_dir, size=500, overlap=64):
    """固定长度切片（约 500 字符、重叠 64）。

    注意：真实系统用的是「按章节优先的语义切片」，这里为了脚本零依赖做了简化。
    这个简化会带来一个可观测的副作用——同一节内容被切口拆散时，检索可能只召回一半。
    """
    chunks = []
    for name in sorted(os.listdir(corpus_dir)):
        if not name.lower().endswith(".txt"):
            continue
        lines = [
            l.strip()
            for l in io.open(os.path.join(corpus_dir, name), encoding="utf-8").read().split("\n")
            if l.strip()
        ]
        buf, cur = [], 0
        for line in lines:
            buf.append(line)
            cur += len(line)
            if cur >= size:
                chunks.append({"src": name, "text": "\n".join(buf)})
                keep, acc = [], 0
                for x in reversed(buf):
                    if acc >= overlap:
                        break
                    keep.insert(0, x)
                    acc += len(x)
                buf, cur = keep, acc
        if buf:
            chunks.append({"src": name, "text": "\n".join(buf)})
    return chunks


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=os.path.join(os.path.dirname(__file__), "corpus"))
    ap.add_argument("--out", default=None)
    ap.add_argument("--ollama", default="http://127.0.0.1:11434")
    ap.add_argument("--embed-model", default="bge-m3")
    ap.add_argument("--model", default="qwen2.5:7b")
    ap.add_argument("--topk", type=int, default=5)
    args = ap.parse_args()

    t0 = time.time()
    chunks = load_chunks(args.corpus)
    if not chunks:
        raise SystemExit("语料目录为空：%s（放入 *.txt 后重试）" % args.corpus)
    print("chunks: %d  chars: %d" % (len(chunks), sum(len(c["text"]) for c in chunks)))

    vectors = [embed(args.ollama, args.embed_model, c["text"]) for c in chunks]
    print("embedding done in %.1fs" % (time.time() - t0))

    results = []
    for q, key in QUESTIONS:
        qv = embed(args.ollama, args.embed_model, q)
        top = sorted(((cosine(qv, v), i) for i, v in enumerate(vectors)), reverse=True)[: args.topk]
        ctx = "\n\n".join(
            "[%d]（来源：%s）\n%s" % (n + 1, chunks[i]["src"], chunks[i]["text"])
            for n, (_, i) in enumerate(top)
        )
        sys_rag = (
            "你是课程助教。只根据下面给出的资料片段回答问题，并用 [编号] 标注引用。"
            "资料里没有的内容就说“资料中没有提到”，不要编造。\n\n" + ctx
        )
        sys_plain = "你是课程助教。请回答学生的问题。"

        a_rag = post(args.ollama, "/api/generate",
                     {"model": args.model, "system": sys_rag, "prompt": q, "stream": False})["response"].strip()
        a_plain = post(args.ollama, "/api/generate",
                       {"model": args.model, "system": sys_plain, "prompt": q, "stream": False})["response"].strip()

        def hit(ans):
            return all(k in ans for k in key.split())

        results.append({
            "question": q,
            "expect_keywords": key,
            "rag_hit": hit(a_rag),
            "plain_hit": hit(a_plain),
            "rag_cites": len(re.findall(r"\[\d+\]", a_rag)),
            "rag_answer": a_rag,
            "plain_answer": a_plain,
            "top_sources": [{"src": chunks[i]["src"], "score": round(s, 4)} for s, i in top],
        })
        print("Q: %s | rag=%s plain=%s" % (q, results[-1]["rag_hit"], results[-1]["plain_hit"]))

    out = args.out or os.path.join(
        os.path.dirname(__file__), "results", time.strftime("%Y-%m-%dT%H%M%S") + ".json"
    )
    os.makedirs(os.path.dirname(out), exist_ok=True)
    payload = {
        "meta": {
            "embed_model": args.embed_model,
            "llm_model": args.model,
            "topk": args.topk,
            "chunks": len(chunks),
            "corpus_chars": sum(len(c["text"]) for c in chunks),
            "elapsed_s": round(time.time() - t0, 1),
            "note": "小样本方法验证，非效果评估；关键词判分",
        },
        "summary": {
            "rag_hit": sum(r["rag_hit"] for r in results),
            "plain_hit": sum(r["plain_hit"] for r in results),
            "total": len(results),
        },
        "results": results,
    }
    io.open(out, "w", encoding="utf-8").write(json.dumps(payload, ensure_ascii=False, indent=2))
    print("\nRAG %d/%d  ·  裸模型 %d/%d" % (
        payload["summary"]["rag_hit"], len(results),
        payload["summary"]["plain_hit"], len(results)))
    print("结果：", out)


if __name__ == "__main__":
    main()
