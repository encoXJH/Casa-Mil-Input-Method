# -*- coding: utf-8 -*-
"""词库规模与关键桶自检。"""
import os
import json
from collections import Counter

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(BASE, 'app', 'src', 'main', 'assets', 'pinyin')


def lines(name):
    with open(os.path.join(D, name), encoding='utf-8') as f:
        return [x.rstrip('\n') for x in f if x.strip() and not x.startswith('#')]


word = lines('word.txt')
abbr = lines('abbr.txt')
single = lines('single.txt')
assoc = lines('assoc.txt')
poly = lines('poly.txt')
syll = lines('syll.txt')

wcount = 0
single_in_word = 0
for line in word:
    parts = line.split('\t')
    if len(parts) < 2:
        continue
    ws = [w for w in parts[1].split('|') if w]
    wcount += len(ws)
    if any(len(w) == 1 for w in ws):
        single_in_word += 1

acount = sum(len([w for w in l.split('\t')[1].split('|') if w]) for l in abbr if '\t' in l)
spos = sum(len(l.split('\t')[1]) for l in single if '\t' in l)
pairs = {l.split('\t')[0]: l.split('\t')[1] for l in word if '\t' in l}
apairs = {l.split('\t')[0]: l.split('\t')[1] for l in abbr if '\t' in l}

stats = {
    'word_buckets': len(word),
    'word_count': wcount,
    'word_lines_with_single': single_in_word,
    'abbr_groups': len(abbr),
    'abbr_words': acount,
    'single_syllables': len(single),
    'single_positions': spos,
    'assoc_lines': len(assoc),
    'poly_groups': len(poly),
    'syllables': len(syll),
}
print(json.dumps(stats, ensure_ascii=False, indent=2))
for k in ['lt', 'nh', 'lx', 'pyin', 'liaotian', 'liaot', 'zhang', 'chang', 'cang', 'zang', 'fuyi', 'huiyi', 'nianxi']:
    print('WORD', k, pairs.get(k))
for k in ['lt', 'nh', 'lx', 'pyin']:
    print('ABBR', k, apairs.get(k))
for k in ['chang', 'hang', 'chong', 'yue', 'huan', 'dei', 'de', 'liao', 'zhuo', 'zhao', 'kuai', 'chai', 'ci', 'zhuan', 'la', 'lao', 'mo', 'jiang', 'sheng', 'xing']:
    p = [l for l in poly if l.startswith(k + '\t')]
    print('POLY', k, p[:1])
for k in ['tian', 'liao', 'lian', 'hui']:
    p = [l for l in assoc if l.startswith(k + '\t')]
    print('ASSOC', k, p[:1])