package com.example.bambi.controller;

import com.example.bambi.Projection.ProductFrequency;
import com.example.bambi.entity.Product;
import com.example.bambi.entity.Size;
import com.example.bambi.repository.ProductRepository;
import com.example.bambi.service.ProductService;
import com.example.bambi.service.SizeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Controller
public class ProductController {

    private final ProductService productService;
    @Autowired
    private SizeService sizeService;

    @Autowired
    private ProductRepository productRepository;

    public ProductController(ProductService productService, SizeService sizeService) {
        super();
        this.productService = productService;
        this.sizeService = sizeService;
    }

    //GET request if no search all listed products shown, if search then only matched products shown
    @GetMapping("/")
    public String listAllProducts(String keyword, Model model) {
        // Default: the home page will be sorted by product name asc.

        return findPaginated(keyword,1, "productName", "asc", model);
    }
    //Handles pagination
    @GetMapping("/products/{pageNo}")
    public String findPaginated(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                @PathVariable(value = "pageNo") int pageNo,
                                @RequestParam("sortField") String sortField,
                                @RequestParam(value = "sortDir") String sortDir,
                                Model model) {

        //keyword will always have a value
        if (keyword == null) {
            keyword = "";
        }

        int pageSize = 5;
        Page<Product> page = productService.findPaginated(keyword, pageNo, pageSize, sortField, sortDir);
        // Get all the products from the database

        // filter the products based on the current page
        List<Product> listProducts = page.getContent();

        model.addAttribute("currentPage", pageNo);
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");
        model.addAttribute("listProducts", listProducts);
        model.addAttribute("keyword", keyword);
        return "products";
    }

        //GET request to retrieve the Add Product Form
    @GetMapping("/products/new")
    public String addProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "add_product";
    }

    //POST request to create a New Product entry
    @PostMapping("/products/new")
    public String addProductForm(@Valid Product product,
                                 BindingResult result,
                                 @Valid @RequestParam(value = "product_image")MultipartFile image,
                                 @RequestParam(value = "size-4", defaultValue = "0") String size4,
                                 @RequestParam(value = "size-5", defaultValue = "0") String size5,
                                 @RequestParam(value = "size-6", defaultValue = "0") String size6,
                                 @RequestParam(value = "size-7", defaultValue = "0") String size7,
                                 @RequestParam(value = "size-8", defaultValue = "0") String size8,
                                 @RequestParam(value = "size-9", defaultValue = "0") String size9,
                                 @RequestParam(value = "size-10", defaultValue = "0") String size10,
                                 @RequestParam(value = "size-11", defaultValue = "0") String size11,
                                 @RequestParam(value = "size-12", defaultValue = "0") String size12,
                                 @RequestParam(value = "size-13", defaultValue = "0") String size13) throws IOException {
        if (result.hasErrors()) {
            return "add_product";
        }
        String filename = image.getOriginalFilename();
        product.setProductImage(filename);

        productService.saveProduct(product);

        String[] sizes = {size4, size5, size6, size7, size8, size9, size10, size11, size12, size13};

        //Loop to get all the values from input // Produces all Strings must convert to int
        for (int i = 4; i <= 13; i++) {
            Size size = new Size();
            size.setProduct(product);
            size.setProductSize(String.valueOf(i));
            size.setProductStock(Integer.valueOf(sizes[i-4]));
            sizeService.saveSize(size);
        }

        saveImageToFolder(image);
        return "redirect:/";
    }

    //GETs the existing product and produces the data in relevant form fields
    @GetMapping("/product/edit/{id}")
    public String editProductForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProductById(id));
        return "edit_product";
    }

    @PostMapping("/product/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid Product product,
                                BindingResult result,
                                @Valid @RequestParam(value = "product_image")MultipartFile productImage) throws IOException {
        if (result.hasErrors()) {
            return "edit_product";
        }

        //Get existing product record
        productService.saveProduct(product);

        //If a file has been uploaded delete the old image
        if (productImage.getOriginalFilename() != "") {
            deleteImage(product.getProductImage());
            saveImageToFolder(productImage);

            //Set existing products new image
            product.setProductImage(productImage.getOriginalFilename());
        } else {
            System.out.println("No File Available");
        }

        productService.updateProduct(product);
        return "redirect:/";
    }

    @GetMapping("/product/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        //Get all sizes related to that shoe
        List<Size> sizes = sizeService.getSizesByProductId(product.getId());

        //Delete each size (each row)
        for (Size size: sizes) {
            sizeService.deleteSizeById(size.getSizeId());
        }
        deleteImage(product.getProductImage());
        productService.deleteProductById(id);
        return "redirect:/";
    }


    //Saves Image To Folder "./bambi-photos/"
    private void saveImageToFolder(MultipartFile image) throws IOException {
        String filename = image.getOriginalFilename();

        String uploadDir = "./bambi-photos/";
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        try (InputStream inputStream = image.getInputStream()) {
            Path filePath = uploadPath.resolve(filename);
            System.out.println(filePath.toFile().getAbsolutePath());
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Could not save uploaded file " + filename);
        }
    }

    //Param - takes image name
    //Deletes image if it exists
    private void deleteImage(String filename) {
        File image = new File("./bambi-photos/" + filename);

        if(image.exists()) {
            image.delete();
        }
    }

    @GetMapping("/data")
    public String showProductsData(Model model) {
        List<ProductFrequency> products = productRepository.findProductFrequency();
        model.addAttribute("products", products);
        model.addAttribute("name", "John");
        Long[] id = new Long[3];
        Long[] frequency = new Long[3];
        String[] shoeNames = new String[3];
        for (int i = 0; i < products.size(); i++) {
            id[i] = products.get(i).getProduct_id();
            frequency[i] = products.get(i).getFrequency();
        }
        for (int i = 0; i < id.length; i++) {
            String productName = productService.getProductById(id[i]).getProductName();
            shoeNames[i] = productName;
        }
        model.addAttribute("id", id);
        model.addAttribute("frequency", frequency);
        model.addAttribute("shoes", shoeNames);
        return "products_data";

    }
}