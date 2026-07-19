import random

random.seed(42)
rows = []
for i in range(71, 111):
    period = f"2025{i:03d}"
    month = (i // 3) % 12 + 1
    day = (i % 28) + 1
    reds = sorted(random.sample(range(1, 34), 6))
    blue = random.randint(1, 16)
    rows.append((period, f"2025-{month:02d}-{day:02d}", ",".join(f"{x:02d}" for x in reds), f"{blue:02d}"))

lines = ["-- Extended seed data for Sprint 1 backtest", "INSERT IGNORE INTO lottery_history (period, draw_date, red_balls, blue_ball) VALUES"]
for idx, (period, draw_date, red_balls, blue_ball) in enumerate(rows):
    suffix = "," if idx < len(rows) - 1 else ";"
    lines.append(f"('{period}', '{draw_date}', '{red_balls}', '{blue_ball}'){suffix}")

with open("database/init/003_seed_extended.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print(f"Generated {len(rows)} rows")
