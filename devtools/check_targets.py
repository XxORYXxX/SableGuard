import re, os, sys, glob
ROOTS = ["/home/claude/sable", "/home/claude/refs/create", "/home/claude/refs/simulated", "/home/claude/refs/carryon"]
src_index = {}
for p in [q for r in ROOTS for q in glob.glob(r + "/**/src/main/java/**/*.java", recursive=True)]:
    m = re.search(r"/java/(.+)\.java$", p); src_index[m.group(1).replace("/", ".")] = p

def method_bodies(src, name):
    out = []
    for m in re.finditer(r"[\w<>\[\],\s?]+\s" + re.escape(name) + r"\s*\(", src):
        i = src.find("{", m.end()); j = src.find(";", m.end())
        if i < 0 or (0 <= j < i and src[m.end():j].count("(") <= src[m.end():j].count(")")): continue
        d = 0
        for k in range(i, len(src)):
            d += src[k] == "{"; d -= src[k] == "}"
            if d == 0: out.append(src[i:k+1]); break
    return out

def check(path):
    s = open(path).read()
    imports = dict((x.split(".")[-1], x) for x in re.findall(r"^import ([\w.]+);", s, re.M))
    tgt = re.search(r'targets\s*=\s*\{?\s*"([\w.$]+)"', s)
    if tgt: fqn = tgt.group(1)
    else:
        m = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.]+)\.class", s)
        if not m: return None
        n = m.group(1); fqn = imports.get(n.split(".")[0], n)
        if "." in n and n.split(".")[0] in imports: fqn = imports[n.split(".")[0]] + "$" + n.split(".",1)[1]
    outer = fqn.split("$")[0]
    if not outer.startswith(("dev.ryanhcode.sable","com.simibubi.create","dev.simulated_team","dev.eriksonn","tschipp")): return (os.path.basename(path), fqn, "VANILLA/OTHER", [])
    if outer not in src_index: return (os.path.basename(path), fqn, "CLASS NOT FOUND", [])
    src = open(src_index[outer]).read()
    problems = []
    for meths in re.findall(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', s):
        for mname in re.findall(r'"([^"]+)"', meths):
            base = mname.split("(")[0]
            if base in ("<init>", "<clinit>"):
                if not re.search(r"\b" + re.escape(outer.split(".")[-1]) + r"\s*\(", src) and base=="<init>": problems.append("ctor?")
                continue
            if not re.search(r"\b" + re.escape(base) + r"\s*\(", src): problems.append(f"method {base} missing")
    for t in re.findall(r'target\s*=\s*"L([\w/$]+);(\w+)', s):
        owner, name = t
        if name in ("<init>",): continue
        if name not in src and not name.startswith("<"): problems.append(f"call {owner.split('/')[-1]}.{name} not in class")
    for a in re.findall(r'@(?:Shadow|Accessor|Invoker)(?:\("(\w+)"\))?[^;{]*?\s(\w+)\s*[;(]', s):
        pass
    return (os.path.basename(path), fqn.replace("dev.ryanhcode.sable.", ""), "OK" if not problems else "CHECK", problems)

for root in sys.argv[1:]:
    print("####", root)
    for p in sorted(glob.glob(root + "/**/mixin/**/*.java", recursive=True)):
        r = check(p)
        if r: print(f"  {r[2]:28s} {r[0]:52s} -> {r[1]}  {'; '.join(r[3])}")
