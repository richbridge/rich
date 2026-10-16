# -*- coding: utf-8 -*-
"""uni-app H5 冒烟（第二轮）：登录 -> 工作台截图"""
import asyncio
from playwright.async_api import async_playwright

BASE = 'http://127.0.0.1:5173'


async def main():
    errors = []
    failed = []
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        ctx = await browser.new_context(
            viewport={'width': 390, 'height': 844},
            device_scale_factor=2, is_mobile=True, has_touch=True)
        page = await ctx.new_page()
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.on('console', lambda m: errors.append('console:' + m.text) if m.type == 'error' else None)
        page.on('requestfailed', lambda r: failed.append(r.url))

        await page.goto(BASE, wait_until='networkidle')
        await page.wait_for_timeout(1200)
        inputs = page.locator('input')
        await inputs.nth(0).fill('admin')
        await inputs.nth(1).fill('123456')
        await page.locator('.login-btn').click()
        await page.wait_for_timeout(4500)
        await page.screenshot(path='uni-home.png', full_page=True)
        print('url:', page.url)
        await browser.close()
    print('errors=%d failed=%d' % (len(errors), len(failed)))
    for e in errors[:10]:
        print(' ', e)


asyncio.run(main())
