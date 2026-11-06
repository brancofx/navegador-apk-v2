from navegador import By, driver

# Página local de teste que acompanha o app
driver.get("file:///android_asset/demo.html")
driver.find_element(By.ID, "nome").send_keys("PyBrowser")
driver.find_element(By.CSS_SELECTOR, "#enviar").click()

print("Título:", driver.title)
print("Resultado:", driver.find_element(By.ID, "resultado").text)
