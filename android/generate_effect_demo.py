#!/usr/bin/env python3
"""
生成落子效果的演示图表
"""

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np
from matplotlib.patches import Circle, FancyBboxPatch
from matplotlib.animation import FuncAnimation
from matplotlib.patches import Rectangle

fig, axes = plt.subplots(2, 3, figsize=(15, 10))
fig.suptitle('象棋落子效果系统演示', fontsize=16, fontweight='bold')

# 时间轴
time_points = np.linspace(0, 1, 5)

# 1. 天天象棋风格
ax = axes[0, 0]
ax.set_title('天天象棋风格\n(TIANTIAN_XIANGQI)', fontweight='bold')
ax.set_xlim(0, 10)
ax.set_ylim(0, 5)
ax.axis('off')

# 展示时间线
for i, t in enumerate(time_points):
    x = 2 + i * 1.8
    y = 4
    
    if t < 0.7:
        # 落地阶段
        scale = 0.3 + (0.13 * t) / 0.7
        alpha = t / 0.7
        circle = Circle((x, y), scale, color='red', alpha=alpha)
        ax.add_patch(circle)
    else:
        # 光晕阶段
        circle = Circle((x, y), 0.43, color='red', alpha=1.0)
        ax.add_patch(circle)
        glow = Circle((x, y), 0.43 + 0.15*np.sin((t-0.7)/(1-0.7)*np.pi), 
                      fill=False, edgecolor='red', alpha=0.3)
        ax.add_patch(glow)
    
    ax.text(x, y-0.8, f'{t:.1%}', ha='center', fontsize=9)

# 2. 简约风格
ax = axes[0, 1]
ax.set_title('简约风格\n(FADE_IN_OUT)', fontweight='bold')
ax.set_xlim(0, 10)
ax.set_ylim(0, 5)
ax.axis('off')

for i, t in enumerate(time_points):
    x = 2 + i * 1.8
    y = 4
    alpha = t / 0.7 if t < 0.7 else 1.0
    circle = Circle((x, y), 0.43, color='blue', alpha=alpha)
    ax.add_patch(circle)
    ax.text(x, y-0.8, f'{t:.1%}', ha='center', fontsize=9)

# 3. 弹跳风格
ax = axes[0, 2]
ax.set_title('弹跳风格\n(BOUNCE)', fontweight='bold')
ax.set_xlim(0, 10)
ax.set_ylim(0, 5)
ax.axis('off')

for i, t in enumerate(time_points):
    x = 2 + i * 1.8
    
    if t < 0.7:
        y = 4 - (1-t/0.7) * 0.3
        scale = 0.8 + (1 - t/0.7) * 0.2
    else:
        bounce = np.sin((t-0.7)/(1-0.7)*np.pi) * 0.15
        y = 3.7 + bounce
        scale = 1.0 + bounce * 0.2
    
    circle = Circle((x, y), scale*0.43, color='green', alpha=1.0)
    ax.add_patch(circle)
    ax.text(x, y-1.2, f'{t:.1%}', ha='center', fontsize=9)

# 4. 脉冲风格
ax = axes[1, 0]
ax.set_title('脉冲风格\n(PULSE)', fontweight='bold')
ax.set_xlim(0, 10)
ax.set_ylim(0, 5)
ax.axis('off')

for i, t in enumerate(time_points):
    x = 2 + i * 1.8
    y = 4
    
    if t < 0.7:
        scale = 0.7 + t / 0.7 * 0.3
        alpha = (t / 0.7) ** 2
    else:
        scale = 1.0
        alpha = 1.0
    
    circle = Circle((x, y), scale*0.43, color='purple', alpha=alpha)
    ax.add_patch(circle)
    
    # 波纹
    for j in range(3):
        pulse = (t - 0.7 + j*0.15) % 1 if t > 0.7 else 0
        if 0 <= pulse <= 1 and t > 0.7:
            pulse_radius = (1.0 + pulse * 0.5) * 0.43
            pulse_alpha = (1 - pulse) * 0.4
            wave = Circle((x, y), pulse_radius, fill=False, 
                         edgecolor='purple', alpha=pulse_alpha, linewidth=1)
            ax.add_patch(wave)
    
    ax.text(x, y-1.2, f'{t:.1%}', ha='center', fontsize=9)

# 5. 无动画
ax = axes[1, 1]
ax.set_title('无动画\n(NONE)', fontweight='bold')
ax.set_xlim(0, 10)
ax.set_ylim(0, 5)
ax.axis('off')

for i, t in enumerate(time_points):
    x = 2 + i * 1.8
    y = 4
    circle = Circle((x, y), 0.43, color='gray', alpha=1.0)
    ax.add_patch(circle)
    ax.text(x, y-0.8, f'{t:.1%}', ha='center', fontsize=9)

# 6. 效果对比表
ax = axes[1, 2]
ax.axis('off')

effects = [
    ('天天象棋', '光晕脉冲', '推荐'),
    ('简约', '淡入淡出', '简洁'),
    ('弹跳', '物理感', '有趣'),
    ('脉冲', '波纹扩散', '科技'),
    ('无动画', '无效果', '快速'),
]

table_y = 4.5
for i, (name, desc, tag) in enumerate(effects):
    y = table_y - i * 0.8
    ax.text(0.5, y, f'● {name}', fontsize=11, fontweight='bold')
    ax.text(3.5, y, desc, fontsize=10)
    ax.text(7, y, f'[{tag}]', fontsize=9, color='blue')

plt.tight_layout()
plt.savefig('piece_drop_effects_demo.png', dpi=150, bbox_inches='tight')
print("✓ 效果演示图已生成: piece_drop_effects_demo.png")

# 创建另一个图表：动画时间线
fig, ax = plt.subplots(figsize=(14, 6))

# 时间轴
timeline = np.linspace(0, 400, 100)  # 400ms

# 不同效果的进度曲线
effects_data = {
    '天天象棋': ('red', timeline / 400),
    '简约': ('blue', np.where(timeline < 280, timeline / 280, 1.0)),
    '弹跳': ('green', np.where(timeline < 280, timeline / 280, 
                              1 - 0.15*np.sin(np.pi*(timeline-280)/(400-280)))),
    '脉冲': ('purple', np.where(timeline < 280, (timeline/280)**2, 1.0)),
}

for effect_name, (color, progress) in effects_data.items():
    ax.plot(timeline, progress, label=effect_name, linewidth=2.5, color=color)

ax.axvline(280, color='gray', linestyle='--', alpha=0.5, label='落地完成 (70%)')
ax.fill_between([0, 280], 0, 1.2, alpha=0.1, color='red', label='落地阶段')
ax.fill_between([280, 400], 0, 1.2, alpha=0.1, color='blue', label='后效应阶段')

ax.set_xlabel('时间 (ms)', fontsize=12, fontweight='bold')
ax.set_ylabel('动画进度', fontsize=12, fontweight='bold')
ax.set_title('不同落子效果的动画进度曲线', fontsize=14, fontweight='bold')
ax.legend(loc='best', fontsize=11)
ax.grid(True, alpha=0.3)
ax.set_xlim(0, 400)
ax.set_ylim(0, 1.2)

plt.tight_layout()
plt.savefig('piece_drop_effects_timeline.png', dpi=150, bbox_inches='tight')
print("✓ 时间线图已生成: piece_drop_effects_timeline.png")

print("\n演示图表生成完成！")
