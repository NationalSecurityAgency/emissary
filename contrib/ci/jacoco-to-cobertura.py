#!/usr/bin/env python3
"""Convert a JaCoCo XML report to Cobertura XML for actions/upload-code-coverage."""

import re
import sys
import time
import xml.etree.ElementTree as ET

JACOCO_XML = "target/site/jacoco/jacoco.xml"
COBERTURA_XML = "cobertura.xml"


def _rate(covered: int, missed: int) -> str:
    total = covered + missed
    return f"{covered / total:.4f}" if total > 0 else "0"


def _counter(elem: ET.Element, kind: str) -> tuple[int, int]:
    c = elem.find(f"counter[@type='{kind}']")
    if c is None:
        return 0, 0
    return int(c.get("covered", 0)), int(c.get("missed", 0))


def convert(jacoco_path: str, cobertura_path: str) -> None:
    with open(jacoco_path, encoding="utf-8") as fh:
        content = fh.read()
    # Strip DOCTYPE declaration — ET cannot resolve the external JaCoCo DTD
    content = re.sub(r"<!DOCTYPE[^>]*>", "", content, flags=re.DOTALL)

    root = ET.fromstring(content)

    lc, lm = _counter(root, "LINE")
    bc, bm = _counter(root, "BRANCH")

    coverage = ET.Element("coverage")
    coverage.set("line-rate", _rate(lc, lm))
    coverage.set("branch-rate", _rate(bc, bm))
    coverage.set("lines-covered", str(lc))
    coverage.set("lines-valid", str(lc + lm))
    coverage.set("branches-covered", str(bc))
    coverage.set("branches-valid", str(bc + bm))
    coverage.set("complexity", "0")
    coverage.set("version", "1")
    coverage.set("timestamp", str(int(time.time() * 1000)))

    sources = ET.SubElement(coverage, "sources")
    ET.SubElement(sources, "source").text = "src/main/java"

    packages_elem = ET.SubElement(coverage, "packages")

    for pkg in root.findall("package"):
        pkg_path = pkg.get("name", "")
        pkg_name = pkg_path.replace("/", ".")
        plc, plm = _counter(pkg, "LINE")
        pbc, pbm = _counter(pkg, "BRANCH")

        pkg_elem = ET.SubElement(packages_elem, "package")
        pkg_elem.set("name", pkg_name)
        pkg_elem.set("line-rate", _rate(plc, plm))
        pkg_elem.set("branch-rate", _rate(pbc, pbm))
        pkg_elem.set("complexity", "0")
        classes_elem = ET.SubElement(pkg_elem, "classes")

        for sf in pkg.findall("sourcefile"):
            sf_name = sf.get("name", "")
            base = sf_name[:-5] if sf_name.endswith(".java") else sf_name
            slc, slm = _counter(sf, "LINE")
            sbc, sbm = _counter(sf, "BRANCH")

            cls_elem = ET.SubElement(classes_elem, "class")
            cls_elem.set("name", f"{pkg_name}.{base}")
            cls_elem.set("filename", f"{pkg_path}/{sf_name}")
            cls_elem.set("line-rate", _rate(slc, slm))
            cls_elem.set("branch-rate", _rate(sbc, sbm))
            cls_elem.set("complexity", "0")
            ET.SubElement(cls_elem, "methods")
            lines_elem = ET.SubElement(cls_elem, "lines")

            for ln in sf.findall("line"):
                ci = int(ln.get("ci", 0))
                cb = int(ln.get("cb", 0))
                mb = int(ln.get("mb", 0))
                line_elem = ET.SubElement(lines_elem, "line")
                line_elem.set("number", ln.get("nr", "0"))
                line_elem.set("hits", "1" if ci > 0 else "0")
                if cb + mb > 0:
                    pct = int(100 * cb / (cb + mb))
                    line_elem.set("branch", "true")
                    line_elem.set("condition-coverage", f"{pct}% ({cb}/{cb + mb})")
                else:
                    line_elem.set("branch", "false")

    ET.indent(coverage)
    with open(cobertura_path, "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="UTF-8"?>\n')
        fh.write(ET.tostring(coverage, encoding="unicode"))
    print(f"Converted {jacoco_path} -> {cobertura_path}")
    print(f"  Lines:    {lc}/{lc + lm} covered  (rate={_rate(lc, lm)})")
    if bc + bm > 0:
        print(f"  Branches: {bc}/{bc + bm} covered  (rate={_rate(bc, bm)})")


if __name__ == "__main__":
    jacoco = sys.argv[1] if len(sys.argv) > 1 else JACOCO_XML
    cobertura = sys.argv[2] if len(sys.argv) > 2 else COBERTURA_XML
    convert(jacoco, cobertura)
